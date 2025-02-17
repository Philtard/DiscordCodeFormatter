package io.horrorshow.codey.compiler;

import io.horrorshow.codey.api.CompilerApi;
import io.horrorshow.codey.api.wandbox.WandboxResponse;
import io.horrorshow.codey.data.repository.ElevatedUserRepository;
import io.horrorshow.codey.data.repository.GithubChannelRepository;
import io.horrorshow.codey.data.repository.Repositories;
import io.horrorshow.codey.data.repository.TimerRepository;
import io.horrorshow.codey.discordutil.ApplicationState;
import io.horrorshow.codey.discordutil.CodeyConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static io.horrorshow.codey.compiler.DiscordCompiler.PLAY;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class DiscordCompilerTest {

    @Mock JDA jda;
    @Mock CompilerApi compilerApi;
    @Mock GithubChannelRepository githubChannelRepository;
    @Mock TimerRepository timerRepository;
    @Mock ElevatedUserRepository elevatedUserRepository;

    DiscordCompiler discordCompiler;
    ApplicationState applicationState;
    CodeyConfig codeyConfig;


    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        var repositories = new Repositories(timerRepository, githubChannelRepository, elevatedUserRepository);
        applicationState = new ApplicationState(jda, repositories);
        codeyConfig = new CodeyConfig();
        discordCompiler = new DiscordCompiler(jda, compilerApi, applicationState);
    }


    @Test
    void doesnt_react_to_bot_messages() {
        var event =
                mock(MessageReceivedEvent.class, RETURNS_DEEP_STUBS);
        when(event.getAuthor().isBot()).thenReturn(true);
        discordCompiler.onMessageReceived(event);
        verify(event, never()).getMessage();
    }


    @Test
    void doesnt_react_to_bot_message_updates() {
        var event =
                mock(MessageUpdateEvent.class, RETURNS_DEEP_STUBS);
        when(event.getAuthor().isBot()).thenReturn(true);
        discordCompiler.onMessageUpdate(event);
        verify(event, never()).getMessage();
    }


    @Test
    void doesnt_react_to_bot_reactions() {
        var event =
                mock(MessageReactionAddEvent.class, RETURNS_DEEP_STUBS);
        when(Objects.requireNonNull(event.getUser()).isBot()).thenReturn(true);
        discordCompiler.onMessageReactionAdd(event);
        verify(event, never()).getReactionEmote();
    }


    @Test
    void on_add_message_compile_code_from_codeblocks_and_add_PLAY_reaction() {
        var message = mock(Message.class, RETURNS_DEEP_STUBS);
        when(message.getContentRaw()).thenReturn("""
                Hello, I'm a discord message
                ```java
                public class A {
                    public static void main(String[] args) {
                        System.out.println("Hello, World!");
                    }
                }```""");
        when(message.getGuild().getId()).thenReturn("guildId");
        when(message.getId()).thenReturn("messageId");

        var wandboxResponse = new WandboxResponse();
        wandboxResponse.setStatus(0);

        when(compilerApi.compile(eq("""
                                
                public class A {
                    public static void main(String[] args) {
                        System.out.println("Hello, World!");
                    }
                }"""), eq("java"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new Output("sysOut", 0, null, null)));

        discordCompiler.onMessage(message);

        verify(message.addReaction(PLAY)).complete();
    }


    @Test
    void on_update_message_compile_code_from_codeblocks_and_add_PLAY_reaction() {
        var message = mock(Message.class, RETURNS_DEEP_STUBS);
        when(message.getContentRaw()).thenReturn("""
                Hello, I'm a discord message
                ```java
                public class A {
                    public static void main(String[] args) {
                        System.out.println("Hello, World!");
                    }
                }```""");
        when(message.getId()).thenReturn("messageId");
        when(message.getGuild().getId()).thenReturn("guildId");

        when(compilerApi.compile(any(), eq("java"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new Output("sysOut", 0, null, null)));

        discordCompiler.onMessage(message);

        verify(message.addReaction(PLAY)).complete();
    }


    @Test
    void on_play_reaction_print_compilation_results_after_compilable_message_received() {
        var message = mock(Message.class, RETURNS_DEEP_STUBS);

        when(message.getContentRaw()).thenReturn("""
                Hello, I'm a discord message
                ```java
                public class A {
                    public static void main(String[] args) {
                        System.out.println("Hello, World!");
                    }
                }```""");
        when(message.getId()).thenReturn("messageId");
        when(message.getGuild().getId()).thenReturn("guildId");

        var channel = mock(TextChannel.class, RETURNS_DEEP_STUBS);
        when(message.getTextChannel()).thenReturn(channel);

        when(compilerApi.compile(eq("""
                                
                public class A {
                    public static void main(String[] args) {
                        System.out.println("Hello, World!");
                    }
                }"""), eq("java"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new Output("sysOut", 0, null, null)));

        discordCompiler.onMessage(message);

        var event =
                mock(MessageReactionAddEvent.class, RETURNS_DEEP_STUBS);
        when(Objects.requireNonNull(event.getUser()).isBot()).thenReturn(false);
        when(event.getReactionEmote().getEmoji()).thenReturn(PLAY);
        when(event.getReactionEmote().isEmoji()).thenReturn(true);
        when(event.getMessageId()).thenReturn("messageId");
        when(event.getChannel().retrieveMessageById("messageId").complete()).thenReturn(message);

        discordCompiler.onReactionAdd(event);

        verify(channel).sendMessage("""
                ```
                sysOut```
                """);
    }

}
