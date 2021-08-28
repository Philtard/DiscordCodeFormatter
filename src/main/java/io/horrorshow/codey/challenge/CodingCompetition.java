package io.horrorshow.codey.challenge;

import io.horrorshow.codey.challenge.xml.Problem;
import io.horrorshow.codey.discordutil.DiscordMessage;
import io.horrorshow.codey.discordutil.DiscordUtils;
import io.horrorshow.codey.discordutil.MessagePart;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;


@Service
@Slf4j
public class CodingCompetition extends ListenerAdapter {

    private static final String CREATE_CHALLENGE = "$create";
    private static final String SHOW_CHALLENGE = "$show";
    private static final String DBG = "$dbg";

    private static final String VERIFY = "\uD83C\uDF00";

    private final List<Problem> problemList;

    private final Map<TextChannel, Challenge> challenges = new HashMap<>();

    private final Random random = new Random();
    private final DiscordUtils utils;
    private final TestRunner testRunner;


    public CodingCompetition(@Autowired JDA jda,
            @Autowired DiscordUtils utils,
            @Autowired ChallengeRepository challengeRepository,
            @Autowired TestRunner testRunner) {

        this.utils = utils;
        this.testRunner = testRunner;

        jda.addEventListener(this);

        problemList = challengeRepository.findAllProblems();
    }


    @Override
    public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        if (event.getMessage().getMentionedUsersBag().contains(event.getJDA().getSelfUser()) &&
            utils.isElevatedMember(event.getMember())) {

            utils.sendRemovableMessage("someone with Codey's Boss role mentioned me!",
                    event.getChannel());
        }

        final var raw = event.getMessage().getContentRaw();
        final var channel = event.getChannel();
        if (CREATE_CHALLENGE.equals(raw)) {
            onCreateChallenge(channel);
        } else if (SHOW_CHALLENGE.equals(raw)) {
            onShowChallenge(channel);
        } else if (DBG.equals(raw) && utils.isElevatedMember(event.getMessage().getMember())) {
            onDbg(event);
        }

        getActiveChallenge(channel).flatMap(challenge -> DiscordMessage.of(raw)
                        .getParts().stream()
                        .filter(MessagePart::isCode)
                        .findAny())
                .ifPresent(p -> event.getMessage().addReaction(VERIFY).queue());
    }


    private Optional<Challenge> getActiveChallenge(TextChannel channel) {
        return Optional.ofNullable(challenges.get(channel))
                .filter(challenge -> challenge.getState() == State.ACTIVE);
    }


    @Override
    public void onGuildMessageReactionAdd(@NotNull GuildMessageReactionAddEvent event) {
        if (event.getUser().isBot()) {
            return;
        }

        var channel = event.getChannel();
        if (VERIFY.equals(event.getReactionEmote().getEmoji())) {
            getActiveChallenge(channel).ifPresentOrElse(challenge ->
                            verifyUserEntry(event, channel, challenge)
                    , () -> utils.sendRemovableMessage(DiscordFormat.noActiveChallenge(), channel));
        }
    }


    private void verifyUserEntry(@NotNull GuildMessageReactionAddEvent event,
            TextChannel channel,
            Challenge challenge) {
        channel.retrieveMessageById(event.getMessageId())
                .queue(message -> DiscordMessage.of(message.getContentRaw())
                        .getParts().stream()
                        .filter(MessagePart::isCode)
                        .forEach(part -> {
                                    var entry = ChallengeEntry.createWithTestRun(testRunner, challenge, message, part);
                                    challenge.getEntries().add(entry);
                                    utils.sendRemovableMessage(DiscordFormat.testResults(challenge, entry), channel);
                                }
                        ));
    }


    private void onCreateChallenge(TextChannel channel) {
        if (problemList.isEmpty()) {
            utils.sendRemovableMessage(DiscordFormat.noChallengesFound(), channel);
        } else {
            var randProblem = problemList.get(random.nextInt(problemList.size()));
            var challenge = new Challenge(randProblem, channel, this);
            challenges.put(channel, challenge);
            utils.sendRemovableMessage(DiscordFormat.presentNewChallenge(challenge), channel);
        }
    }


    private void onShowChallenge(TextChannel channel) {
        if (challenges.containsKey(channel)) {
            var challenge = challenges.get(channel);
            switch (challenge.getState()) {
                case ACTIVE -> utils.sendRemovableMessage(DiscordFormat.showCurChallenge(challenge), channel);
                case DONE -> utils.sendRemovableMessage(DiscordFormat.challengeDone(challenge), channel);
            }
        } else {
            utils.sendRemovableMessage(DiscordFormat.noChallengeInChannelMsg(), channel);
        }
    }


    public void onChallengeTimeUp(Challenge challenge) {
        utils.sendRemovableMessage(DiscordFormat.challengeFinishedMsg(challenge), challenge.getChannel());
    }


    private void onDbg(GuildMessageReceivedEvent event) {
        var img = DiscordFormat
                .drawSomeAvatarsToSeeHowThatLooksLike(challenges.get(event.getChannel()));
        utils.drawRemovableImage(img, "test.png", event.getChannel());
    }
}
