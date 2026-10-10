package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.SurvivalGames;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The placeholders of PlaceholderAPI: %sg_&lt;placeholder&gt;%, see {@link Placeholders}. Only loaded if
 * PlaceholderAPI is installed.
 *
 * @author Christian34
 */
public class SgExpansion extends PlaceholderExpansion {
    private final SurvivalGames plugin;

    public SgExpansion(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "sg";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Christian34";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        return Placeholders.value(plugin, player, params);
    }

}
