package io.horrorshow.codey.time;

import io.horrorshow.codey.discordutil.CodeyConfig;
import io.horrorshow.codey.discordutil.DiscordUtils;
import io.horrorshow.codey.parser.TimeParser;
import io.horrorshow.codey.util.CodeyTask;
import io.horrorshow.codey.util.TaskInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.text.DateFormatSymbols;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Service
public class DiscordTimezone extends ListenerAdapter {

    private static final Pattern timeMatcher = Pattern.compile(createTimeMatchPattern(), Pattern.CASE_INSENSITIVE);
    private final CodeyConfig codeyConfig;


    @Autowired
    public DiscordTimezone(JDA jda, CodeyConfig codeyConfig) {
        this.codeyConfig = codeyConfig;

        jda.addEventListener(this);
    }


    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.getAuthor().isBot()) {
            CodeyTask.runAsync(() -> onMessage(event.getMessage()), new TaskInfo(event));
        }
    }


    void onMessage(Message message) {
        var matcher = timeMatcher.matcher(message.getContentStripped());

        while (matcher.find()) {
            OffsetDateTime timestamp = TimeParser.toOffsetDateTime(matcher.group());
            var reply = new EmbedBuilder()
                    .setFooter("Time in your timezone: ")
                    .setTimestamp(timestamp)
                    .setColor(Color.decode(codeyConfig.getEmbedColor())).build();
            DiscordUtils.sendRemovableMessageReply(message, reply);
        }
    }


    private static String createTimeMatchPattern() {
        return Arrays.stream(new DateFormatSymbols().getWeekdays()).collect(Collectors.joining("|", "(", ")?"))
                + "( )?([0-9]|0[0-9]|1[0-9]|2[0-3]):[0-5][0-9] "
                + ZoneId.getAvailableZoneIds().stream()
                .map(zone -> zone.replaceAll("\\+", "\\\\+"))
                .collect(Collectors.joining("|", "(", ")"));
    }

}
