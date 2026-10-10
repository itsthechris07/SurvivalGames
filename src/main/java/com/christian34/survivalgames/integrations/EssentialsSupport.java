package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.GameWorld;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * EssentialsX: god mode, vanish and AFK are turned off when a player joins a game (god mode and vanish come back
 * when he leaves it), /god, /fly and /vanish don't work while playing, and Essentials teleports (/back, /tpa, /home,
 * ...) don't lead into the worlds of games.
 *
 * @author Christian34
 */
public interface EssentialsSupport {
    EssentialsSupport NONE = new EssentialsSupport() {
    };

    /**
     * the player joins a game or watches it
     *
     * @param spectator watching only: just AFK is turned off
     */
    default void joinGame(@NotNull Player player, boolean spectator) {
    }

    /**
     * the player has left the game (he isn't in it any more)
     */
    default void leaveGame(@NotNull Player player) {
    }

    /**
     * @return true if the player is vanished by Essentials (he becomes visible when joining a game)
     */
    default boolean isVanished(@NotNull Player player) {
        return false;
    }

    /**
     * @return true if the player plays a game (not watching): no god mode, flying or vanish
     */
    static boolean isPlaying(@NotNull Player player) {
        return SurvivalGames.getInstance().getUser(player).isPlaying();
    }

    /**
     * @return true if an Essentials teleport of the player (/back, /tpa, /home, ...) must not happen: into the world
     * of a game from outside, or within it while playing
     */
    static boolean blocksTeleport(@NotNull Player player, @Nullable Location to) {
        if (to == null || !GameWorld.isPluginWorld(to.getWorld())) return false;
        return !to.getWorld().equals(player.getWorld()) || isPlaying(player);
    }

    static EssentialsSupport create(SurvivalGames plugin) {
        if (!Bukkit.getPluginManager().isPluginEnabled(EssentialsHook.PLUGIN)) return NONE;
        try {
            EssentialsHook hook = new EssentialsHook();
            Bukkit.getPluginManager().registerEvents(hook, plugin);
            Debug.info("Hooked into EssentialsX");
            return hook;
        } catch (Exception | LinkageError ex) {
            Debug.warn("Couldn't hook into EssentialsX: " + ex);
            return NONE;
        }
    }

}
