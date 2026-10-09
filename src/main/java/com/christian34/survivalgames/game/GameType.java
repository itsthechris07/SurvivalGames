package com.christian34.survivalgames.game;

import com.christian34.survivalgames.lib.lang.LangText;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The games an arena can host. Their settings are the section of config.yml with the key of the type.
 *
 * @author Christian34
 */
public enum GameType {
    /**
     * a round of a few minutes: loot from the chests, the last one standing wins, the map is reset afterwards
     */
    SURVIVAL_GAMES("survival-games", LangText.TYPE_SURVIVAL_GAMES),
    /**
     * runs for days in the same world: every player may only play a few minutes per day
     */
    VARO("varo", LangText.TYPE_VARO);

    private final String key;
    private final LangText displayName;

    GameType(String key, LangText displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    /**
     * the key in arenas.yml and the section of config.yml
     */
    public String getKey() {
        return key;
    }

    public LangText getDisplayName() {
        return displayName;
    }

    /**
     * @param name the key (survival-games, varo) or a short form (sg)
     * @return null if there is no such type
     */
    @Nullable
    public static GameType parse(@Nullable String name) {
        if (name == null) return null;
        String key = name.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (key.equals("sg")) return SURVIVAL_GAMES;
        for (GameType type : values()) {
            if (type.key.equals(key)) return type;
        }
        return null;
    }

    public static List<String> keys() {
        return Arrays.stream(values()).map(GameType::getKey).toList();
    }

}
