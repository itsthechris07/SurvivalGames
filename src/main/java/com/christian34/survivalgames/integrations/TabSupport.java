package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * TAB (NEZNAMY): while a player is in a game, TAB doesn't overwrite his name in the tab list (team color), the name
 * tags (team colors) and the sidebar of the game. Everything of TAB comes back when he leaves the game.
 *
 * @author Christian34
 */
public interface TabSupport {
    TabSupport NONE = new TabSupport() {
        @Override
        public void takeOver(@NotNull Player player, @Nullable Component tabName, boolean sidebar) {
        }

        @Override
        public void release(@NotNull Player player) {
        }
    };

    /**
     * the player is in a game (called every second, TAB is only told about changes)
     *
     * @param tabName the name in the tab list, null: the one of TAB (no team yet)
     * @param sidebar true: the sidebar of the game is shown (TAB hides its own)
     */
    void takeOver(@NotNull Player player, @Nullable Component tabName, boolean sidebar);

    /**
     * the player isn't in a game (any more): TAB shows its things again
     */
    void release(@NotNull Player player);

    static TabSupport create(SurvivalGames plugin) {
        if (!Bukkit.getPluginManager().isPluginEnabled(TabHook.PLUGIN)) return NONE;
        try {
            TabSupport hook = new TabHook(plugin);
            Debug.info("Hooked into TAB (tab list names, name tags and sidebar in games)");
            return hook;
        } catch (Exception | LinkageError ex) {
            Debug.warn("Couldn't hook into TAB, it may overwrite the tab list and name tags in games: " + ex);
            return NONE;
        }
    }

}
