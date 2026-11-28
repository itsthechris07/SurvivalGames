package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.stats.PlayerStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Training scores (modules.training-score): when the players stand on their spawn points everybody gets a score from
 * 1 to 12 - 6 for the start rating, one more (less) for every points-per-score rating points above (below), plus a
 * little mood of the Gamemakers (random, up to +-random). The scores are announced, the best first.
 *
 * @author Christian34
 */
public class TrainingScores extends GameModule {
    public static final String MODULE = "training-score";

    public TrainingScores(SurvivalGames plugin) {
        super(plugin, MODULE);
    }

    /**
     * @return the score (1-12) of a player with the rating
     */
    public int score(int rating) {
        int points = Math.max(1, config().getInt("points-per-score", 50));
        int random = Math.max(0, config().getInt("random", 1));
        int mood = random == 0 ? 0 : ThreadLocalRandom.current().nextInt(-random, random + 1);
        return Math.clamp(6 + Math.round((rating - PlayerStats.RATING_START) / (float) points) + mood, 1, 12);
    }

    /**
     * the game is starting: every participant gets his score, everybody sees all of them
     */
    public void announce(Game game) {
        if (!isEnabled(game)) return;
        List<Participant> participants = game.getParticipants().stream().filter(Participant::isAlive).toList();
        for (Participant participant : participants) {
            participant.setScore(score(plugin.getStats().getRating(participant.getUuid(), game.getType())));
        }
        List<Component> entries = participants.stream()
                .sorted(Comparator.comparingInt(Participant::getScore).reversed().thenComparing(Participant::getName))
                .map(participant -> I.i18n(LangText.TRAINING_SCORE_ENTRY, participant.getName(),
                        Component.text(participant.getScore(), color(participant.getScore()))))
                .toList();
        game.broadcast(LangText.TRAINING_SCORES, Component.join(JoinConfiguration.commas(true), entries));
        for (Participant participant : participants) {
            Player player = participant.getPlayer();
            if (player != null) {
                player.sendMessage(game.prefixed(LangText.TRAINING_SCORE_OWN,
                        Component.text(participant.getScore(), color(participant.getScore()))));
            }
        }
    }

    private static NamedTextColor color(int score) {
        if (score >= 10) return NamedTextColor.GOLD;
        if (score >= 7) return NamedTextColor.GREEN;
        if (score >= 4) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

}
