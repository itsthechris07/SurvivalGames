package com.christian34.survivalgames.game;

import com.christian34.survivalgames.lib.lang.LangText;
import org.bukkit.Material;
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
    VARO("varo", LangText.TYPE_VARO),
    /**
     * a round in a newly generated world: no loot, no natural regeneration, the world border pushes everybody into
     * the center
     */
    UHC("uhc", LangText.TYPE_UHC),
    /**
     * two players (or teams) with the same kit on a small map - from the queue or a challenge
     */
    DUELS("duels", LangText.TYPE_DUELS);

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
     * the item of the game type in the menus
     */
    public Material getIcon() {
        return switch (this) {
            case SURVIVAL_GAMES -> Material.IRON_SWORD;
            case VARO -> Material.GRASS_BLOCK;
            case UHC -> Material.GOLDEN_APPLE;
            case DUELS -> Material.DIAMOND_SWORD;
        };
    }

    /**
     * @param name the key (survival-games, varo, uhc, duels) or a short form (sg, duel)
     * @return null if there is no such type
     */
    @Nullable
    public static GameType parse(@Nullable String name) {
        if (name == null) return null;
        String key = name.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (key.equals("sg")) return SURVIVAL_GAMES;
        if (key.equals("duel")) return DUELS;
        for (GameType type : values()) {
            if (type.key.equals(key)) return type;
        }
        return null;
    }

    public static List<String> keys() {
        return Arrays.stream(values()).map(GameType::getKey).toList();
    }

}
