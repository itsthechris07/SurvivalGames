package com.christian34.survivalgames.stats;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

/**
 * The statistics of a player in one game type (or in all of them).
 *
 * @param rating the difference of the Elo rating to {@link #RATING_START} (see {@link #get})
 * @author Christian34
 */
public record PlayerStats(UUID uuid, String name, int games, int wins, int kills, int deaths, int rating) {
    /**
     * the Elo rating of new players
     */
    public static final int RATING_START = 1000;

    public static PlayerStats empty(UUID uuid, String name) {
        return new PlayerStats(uuid, name, 0, 0, 0, 0, 0);
    }

    /**
     * @return the sum of the statistics (e.g. of all game types)
     */
    public static PlayerStats total(UUID uuid, String name, Collection<PlayerStats> stats) {
        PlayerStats total = empty(uuid, name);
        for (PlayerStats other : stats) {
            total = new PlayerStats(uuid, name, total.games + other.games, total.wins + other.wins,
                    total.kills + other.kills, total.deaths + other.deaths, total.rating + other.rating);
        }
        return total;
    }

    /**
     * @return the value of the statistic (the rating itself, not the difference)
     */
    public int get(Stat stat) {
        return switch (stat) {
            case GAMES -> games;
            case WINS -> wins;
            case KILLS -> kills;
            case DEATHS -> deaths;
            case RATING -> RATING_START + rating;
        };
    }

    /**
     * @return the statistics with the amount added (and the current name)
     */
    public PlayerStats plus(Stat stat, int amount, String name) {
        return new PlayerStats(uuid, name, games + (stat == Stat.GAMES ? amount : 0),
                wins + (stat == Stat.WINS ? amount : 0), kills + (stat == Stat.KILLS ? amount : 0),
                deaths + (stat == Stat.DEATHS ? amount : 0), rating + (stat == Stat.RATING ? amount : 0));
    }

    /**
     * @return true if the player is in the list of the best players of the statistic: a value above 0, the rating once
     * he has played
     */
    public boolean isRanked(Stat stat) {
        return stat == Stat.RATING ? games > 0 : get(stat) > 0;
    }

    /**
     * @return kills per death (the kills without deaths)
     */
    public double getKillDeathRatio() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }

    /**
     * e.g. 1.50
     */
    public String formatKillDeathRatio() {
        return String.format(Locale.ROOT, "%.2f", getKillDeathRatio());
    }

}
