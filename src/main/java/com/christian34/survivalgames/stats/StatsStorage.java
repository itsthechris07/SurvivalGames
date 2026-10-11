package com.christian34.survivalgames.stats;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where the statistics are kept: stats.yml or a database. Only used by one thread at a time (the thread of the
 * {@link StatsManager}), errors are thrown as runtime exceptions.
 *
 * @author Christian34
 */
public interface StatsStorage {

    /**
     * adds the amount to the statistic of the player in the game type (and saves his current name)
     */
    void add(UUID uuid, String name, String type, Stat stat, int amount);

    /**
     * @return the statistics of the player by game type (empty if he has none)
     */
    Map<String, PlayerStats> load(UUID uuid);

    /**
     * @return the player with this name (ignoring the case), null if he has no statistics
     */
    @Nullable
    UUID find(String name);

    /**
     * @param type a game type, null: the sums of all game types
     * @return the players with the highest values of the statistic (only values above 0), the best one first
     */
    List<PlayerStats> top(Stat stat, @Nullable String type, int limit);

    /**
     * @return the achievements of the player (key of the achievement - when he got it, milliseconds since 1970)
     */
    Map<String, Long> loadAchievements(UUID uuid);

    /**
     * the player gets the achievement
     *
     * @return false if he has it already
     */
    boolean addAchievement(UUID uuid, String name, String achievement, long time);

    void close();

}
