package com.christian34.survivalgames.stats;

import com.christian34.survivalgames.lib.lang.LangText;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

/**
 * The achievements of the players (stats.achievements in config.yml). Some are reached with the statistics of all
 * game types, the others in a game.
 *
 * @author Christian34
 */
public enum Achievement {
    FIRST_GAME(Material.WOODEN_SWORD, Stat.GAMES, 1),
    FIRST_KILL(Material.STONE_SWORD, Stat.KILLS, 1),
    FIRST_WIN(Material.GOLD_INGOT, Stat.WINS, 1),
    VETERAN(Material.IRON_CHESTPLATE, Stat.GAMES, 50),
    HUNTER(Material.DIAMOND_SWORD, Stat.KILLS, 100),
    CHAMPION(Material.GOLDEN_HELMET, Stat.WINS, 10),
    LEGEND(Material.NETHER_STAR, Stat.WINS, 50),
    /**
     * the first kill of a game
     */
    FIRST_BLOOD(Material.REDSTONE),
    /**
     * 3 kills in one game
     */
    RAMPAGE(Material.BLAZE_POWDER),
    /**
     * 5 kills in one game
     */
    UNSTOPPABLE(Material.TNT),
    /**
     * won without wearing armor
     */
    NAKED(Material.LEATHER_CHESTPLATE),
    /**
     * won without a kill
     */
    PACIFIST(Material.POPPY),
    /**
     * won with one heart or less
     */
    LAST_BREATH(Material.GOLDEN_APPLE),
    /**
     * got the bounty on a player
     */
    BOUNTY_HUNTER(Material.EMERALD),
    /**
     * sent an item to a player as a spectator
     */
    SPONSOR(Material.CHEST),
    /**
     * won a Varo alive
     */
    VARO_SURVIVOR(Material.TOTEM_OF_UNDYING),
    /**
     * won a Varo without a strike
     */
    VARO_SPOTLESS(Material.PAPER);

    /**
     * the kills in one game for {@link #RAMPAGE} and {@link #UNSTOPPABLE}
     */
    public static final int RAMPAGE_KILLS = 3;
    public static final int UNSTOPPABLE_KILLS = 5;

    private final Material icon;
    @Nullable
    private final Stat stat;
    private final int goal;

    Achievement(Material icon) {
        this(icon, null, 0);
    }

    Achievement(Material icon, @Nullable Stat stat, int goal) {
        this.icon = icon;
        this.stat = stat;
        this.goal = goal;
    }

    /**
     * the key in stats.yml and the database
     */
    public String getKey() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public Material getIcon() {
        return icon;
    }

    /**
     * @return the statistic (of all game types) that has to reach {@link #getGoal()}, null: reached in a game
     */
    @Nullable
    public Stat getStat() {
        return stat;
    }

    public int getGoal() {
        return goal;
    }

    public LangText getDisplayName() {
        return LangText.valueOf("ACHIEVEMENT_" + name() + "_NAME");
    }

    public LangText getDescription() {
        return LangText.valueOf("ACHIEVEMENT_" + name() + "_DESCRIPTION");
    }

}
