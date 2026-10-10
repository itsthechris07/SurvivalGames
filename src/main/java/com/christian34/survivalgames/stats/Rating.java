package com.christian34.survivalgames.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Elo rating for games with many players: every player is compared with every player of the other teams - the
 * one who lasted longer beat the other one. The change is divided by the number of opponents, so a game counts like
 * one duel, no matter how many players took part.
 *
 * @author Christian34
 */
public final class Rating {

    private Rating() {
    }

    /**
     * @param scores  how long every player (his team) lasted, higher is better - teammates have the same score
     * @param ratings the ratings of the players before the game
     * @param factor  the greatest change of a game (K-factor)
     * @return the change of the rating of every player
     */
    public static Map<UUID, Integer> changes(Map<UUID, Integer> scores, Map<UUID, Integer> ratings, double factor) {
        Map<UUID, Integer> changes = new HashMap<>();
        for (Map.Entry<UUID, Integer> player : scores.entrySet()) {
            int rating = ratings.getOrDefault(player.getKey(), PlayerStats.RATING_START);
            double sum = 0;
            int opponents = 0;
            for (Map.Entry<UUID, Integer> other : scores.entrySet()) {
                // teammates (and players out at the same time) aren't compared
                if (other.getValue().equals(player.getValue())) continue;
                int otherRating = ratings.getOrDefault(other.getKey(), PlayerStats.RATING_START);
                double expected = 1 / (1 + Math.pow(10, (otherRating - rating) / 400.0));
                sum += (player.getValue() > other.getValue() ? 1 : 0) - expected;
                opponents++;
            }
            changes.put(player.getKey(), opponents == 0 ? 0 : (int) Math.round(factor * sum / opponents));
        }
        return changes;
    }

}
