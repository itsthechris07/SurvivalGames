package com.christian34.survivalgames.stats;

import com.christian34.survivalgames.lib.lang.LangText;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The statistics of a player (per game type).
 *
 * @author Christian34
 */
public enum Stat {
    /**
     * games the player has started in
     */
    GAMES("games", LangText.STAT_GAMES),
    /**
     * games his team has won
     */
    WINS("wins", LangText.STAT_WINS),
    KILLS("kills", LangText.STAT_KILLS),
    /**
     * times he was out of the game (died, left the running game, strikes)
     */
    DEATHS("deaths", LangText.STAT_DEATHS),
    /**
     * the Elo rating (stats.rating), saved as the difference to {@link PlayerStats#RATING_START}
     */
    RATING("rating", LangText.STAT_RATING);

    private final String key;
    private final LangText displayName;

    Stat(String key, LangText displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    /**
     * the key in stats.yml, the column of the database and the name in commands and placeholders
     */
    public String getKey() {
        return key;
    }

    public LangText getDisplayName() {
        return displayName;
    }

    /**
     * @return null if there is no such statistic
     */
    @Nullable
    public static Stat parse(@Nullable String name) {
        if (name == null) return null;
        String key = name.trim().toLowerCase(Locale.ROOT);
        for (Stat stat : values()) {
            if (stat.key.equals(key)) return stat;
        }
        return null;
    }

    public static List<String> keys() {
        return Arrays.stream(values()).map(Stat::getKey).toList();
    }

}
