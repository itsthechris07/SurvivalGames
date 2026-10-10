package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.GameWorld;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Multiverse: the worlds of games and of edited maps (sg_*, sgvaro_*) are left alone.
 * <ul>
 *     <li>Multiverse-Core imports worlds of other plugins (auto-import-3rd-party-worlds) and would then enforce its
 *     game mode etc. there and load them again after a restart - they are removed from it again.</li>
 *     <li>Multiverse-Inventories would swap the inventory when players enter or leave the worlds of games (the game
 *     saves and restores it itself, see UserStorage) - it doesn't handle them while they are in a game.</li>
 * </ul>
 *
 * @author Christian34
 */
public final class MultiverseSupport {
    static final String CORE = "Multiverse-Core";
    static final String INVENTORIES = "Multiverse-Inventories";

    private MultiverseSupport() {
    }

    /**
     * before the leftover worlds of games are deleted (Multiverse may have loaded them again)
     */
    public static void hook(SurvivalGames plugin) {
        if (Bukkit.getPluginManager().isPluginEnabled(CORE)) {
            try {
                MultiverseCoreHook hook = new MultiverseCoreHook(plugin);
                Bukkit.getPluginManager().registerEvents(hook, plugin);
                hook.removeGameWorlds();
                Debug.info("Hooked into Multiverse-Core (the worlds of games aren't managed by it)");
            } catch (Exception | LinkageError ex) {
                Debug.warn("Couldn't hook into Multiverse-Core: " + ex);
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled(INVENTORIES)) {
            try {
                Bukkit.getPluginManager().registerEvents(new MultiverseInventoriesHook(), plugin);
                Debug.info("Hooked into Multiverse-Inventories (no inventory groups in the worlds of games)");
            } catch (Exception | LinkageError ex) {
                Debug.warn("Couldn't hook into Multiverse-Inventories, it may swap inventories in games: " + ex);
            }
        }
    }

    /**
     * @return true for the worlds of games and of edited maps
     */
    static boolean isGameWorld(@Nullable String world) {
        return world != null && (world.startsWith(GameWorld.TEMP_PREFIX) || world.startsWith(GameWorld.KEPT_PREFIX));
    }

    /**
     * @return true for the worlds that are deleted when they are closed (a Varo keeps its world)
     */
    static boolean isTemporary(String world) {
        return world.startsWith(GameWorld.TEMP_PREFIX);
    }

    /**
     * @param from the world the player leaves, null if he doesn't change the world
     * @param to   the world the player enters, null if he doesn't change the world
     * @return true if Multiverse-Inventories mustn't save or load the inventory of the player: he is in a game (his
     * real things are saved by the game) or he enters or leaves the world of a game
     */
    public static boolean ignoresInventory(@NotNull Player player, @Nullable String from, @Nullable String to) {
        return isGameWorld(from) || isGameWorld(to) || isGameWorld(player.getWorld().getName())
                || SurvivalGames.getInstance().getUser(player).getStorage().isStored();
    }

}
