package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

/**
 * A module that can be switched on per game type (config.yml: modules.&lt;key&gt;.enabled and .games).
 *
 * @author Christian34
 */
public abstract class GameModule {
    protected final SurvivalGames plugin;
    private final String key;

    protected GameModule(SurvivalGames plugin, String key) {
        this.plugin = plugin;
        this.key = key;
        plugin.registerModule(this);
    }

    /**
     * every second of a running game in which the module is used
     */
    public void tick(Game game) {
    }

    /**
     * the game stops (its data in {@link Game#getModuleData} goes with it)
     */
    public void stop(Game game) {
    }

    /**
     * @return the key of the module in config.yml (modules.&lt;key&gt;)
     */
    public String getKey() {
        return key;
    }

    /**
     * @return true if the module is switched on for the type of the game
     */
    protected final boolean isEnabled(@Nullable Game game) {
        return plugin.isModuleEnabled(key, game);
    }

    /**
     * @return true if the module is used in the game (a module may have more conditions, e.g. solo games only)
     */
    public boolean isUsed(@Nullable Game game) {
        return isEnabled(game);
    }

    /**
     * @return the settings of the module (modules.&lt;key&gt;)
     */
    protected ConfigurationSection config() {
        ConfigurationSection section = plugin.getFileManager().getConfigFile().getSection("modules." + key);
        return section != null ? section : plugin.getFileManager().getConfigFile().getData()
                .createSection("modules." + key);
    }

}
