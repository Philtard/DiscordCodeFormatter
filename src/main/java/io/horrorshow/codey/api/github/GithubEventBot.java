package io.horrorshow.codey.api.github;

import io.horrorshow.codey.discordutil.ApplicationState;
import io.horrorshow.codey.discordutil.DiscordUtils;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


@Service
@Slf4j
public class GithubEventBot {

    private final DiscordUtils discordUtils;
    private final GithubEventState githubEventState;


    @Autowired
    public GithubEventBot(DiscordUtils discordUtils, ApplicationState applicationState) {
        this.discordUtils = discordUtils;
        this.githubEventState = applicationState.getGithubEventState();
    }


    @Async
    public void onPush(GithubWebhookEndpoint.GithubPush event) {
        log.info("onPush:\n{}", event);
        var embed = new EmbedBuilder()
                .setTimestamp(Instant.ofEpochSecond(Long.parseLong(event.repository.pushed_at)))
                .setThumbnail(event.sender.avatar_url)
                .setTitle("Github push by " + event.pusher.name)
                .addField("Repo", String.format("[%s](%s)", event.repository.name, event.repository.url), true)
                .addField("Pusher", event.pusher.name, true)
                .addField("Commits", event.commits.stream()
                        .map(this::formatCommit)
                        .collect(Collectors.joining("\n")), false)
                .addField("Repository language", event.repository.language, true)
                .addField("Repository Owner", event.repository.owner.name, true)
                .setFooter(String.format("git clone %s", event.repository.clone_url))
                .build();

        CompletableFuture.allOf(
                githubEventState.getAll().stream()
                        .map(channelInfo -> {
                            var channel = channelInfo.channel();
                            var textChannel = channel.getJDA().getTextChannelById(channel.getId());
                            if (textChannel != null) {
                                return discordUtils.sendRemovableEmbed(embed, textChannel);
                            }
                            return null;
                        }).toArray(CompletableFuture[]::new)
        ).exceptionally(e -> {
            log.error("error sending github update to text channel", e);
            return null;
        });
    }


    private String formatCommit(GithubWebhookEndpoint.GithubCommit commit) {
        return String.format("[%s](%s) (%d files)", commit.message, commit.url, commit.modified.size());
    }

}
