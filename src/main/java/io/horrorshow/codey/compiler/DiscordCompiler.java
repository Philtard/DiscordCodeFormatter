package io.horrorshow.codey.compiler;

import io.horrorshow.codey.api.CompilerApi;
import io.horrorshow.codey.discordutil.ApplicationState;
import io.horrorshow.codey.discordutil.DiscordMessage;
import io.horrorshow.codey.discordutil.DiscordUtils;
import io.horrorshow.codey.discordutil.MessagePart;
import io.horrorshow.codey.parser.SourceProcessing;
import io.horrorshow.codey.util.CodeyTask;
import io.horrorshow.codey.util.DoOr;
import io.horrorshow.codey.util.TaskInfo;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

import static io.horrorshow.codey.discordutil.DiscordUtils.CHAR_LIMIT;
import static io.horrorshow.codey.discordutil.DiscordUtils.toCodeBlock;
import static io.horrorshow.codey.discordutil.DiscordUtils.truncateMessage;


@Service
@Slf4j
public class DiscordCompiler extends ListenerAdapter {

    public static final String PLAY = "▶️";

    private final CompilerApi compiler;
    private final ApplicationState.CompilationCache compilationCache;


    public DiscordCompiler(@Autowired JDA jda,
            @Autowired @Qualifier("piston") CompilerApi compiler,
            @Autowired ApplicationState applicationState) {
        this.compilationCache = applicationState.getCompilationCache();
        this.compiler = compiler;

        jda.addEventListener(this);
    }


    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.getAuthor().isBot()) {
            CodeyTask.runAsync(() -> onMessage(event.getMessage()), new TaskInfo(event));
        }
    }


    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        if (!event.getAuthor().isBot()) {
            CodeyTask.runAsync(() -> onMessage(event.getMessage()), new TaskInfo(event));
        }
    }


    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        if (!DoOr.getOrThrow(event::getUser, "Missing user in event").isBot()) {
            CodeyTask.runAsync(() -> onReactionAdd(event), new TaskInfo(event));
        }
    }


    public void onReactionAdd(@NotNull MessageReactionAddEvent event) {
        if (DiscordUtils.hasEmoji(PLAY, event)) {
            var message = event.getChannel().retrieveMessageById(event.getMessageId()).complete();
            sendCompilationResults(message);
        }
    }


    public void sendCompilationResults(Message message) {
        if (compilationCache.hasResult(message)) {
            var futures = compilationCache
                    .get(message).stream()
                    .map(msg -> {
                        if (msg.length() > CHAR_LIMIT) {
                            return DiscordUtils.sendTextFile("codey-compiler-output.txt", msg, message.getTextChannel());
                        } else {
                            return DiscordUtils.sendRemovableMessageAsync(toCodeBlock(msg, true), message.getTextChannel());
                        }
                    })
                    .toList();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
            log.info("send compilation results of msg={} content={}", message.getId(),
                    truncateMessage(message.getContentRaw(), 100));
        } else {
            onMessage(message);
            DiscordUtils.sendRemovableMessageAsync("Compilation result unavailable! "
                                                   + "Codey will try to compile it again, "
                                                   + "try again in a few seconds.", message.getTextChannel());
            log.info("compilation results not available, msg={} content={}", message.getId(),
                    truncateMessage(message.getContentRaw(), 100));
        }
    }


    public void onMessage(Message message) {
        var contentRaw = message.getContentRaw();
        var dm = DiscordMessage.of(contentRaw);
        compileCodeBlocks(message, dm);
    }


    private void compileCodeBlocks(@NotNull Message message, DiscordMessage dm) {
        dm.getParts().stream()
                .filter(MessagePart::isCode)
                .findFirst()
                .ifPresent(part -> {
                    var processed = SourceProcessing.processSource(part.text(), part.lang());
                    if (processed.isOk()) {
                        log.info("compiling\n{}", processed);
                        compiler.compile(processed.source(), part.lang(), null, null)
                                .thenAccept(output -> cacheAndAddPlayReaction(message, output));
                    } else {
                        log.info("source process failed\n{}", processed);
                        var errorOut = new Output(null, 1, null, processed.error());
                        cacheAndAddPlayReaction(message, errorOut);
                    }
                });
    }


    private void cacheAndAddPlayReaction(@NotNull Message message, Output errorOut) {
        compilationCache.cache(message, DiscordUtils.compilerOutToDiscordMessage(errorOut));
        message.addReaction(PLAY).complete();
    }
}
