package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.SurvivalGames;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.jetbrains.annotations.NotNull;

/**
 * Vanished players (SuperVanish, PremiumVanish, EssentialsX, CMI, ... - they all set the metadata "vanished") aren't
 * put into games automatically (game server, party members) and can only play once they are visible again. Players
 * vanished by EssentialsX become visible when they join ({@link EssentialsSupport}).
 *
 * @author Christian34
 */
public final class VanishSupport {
    /**
     * the metadata vanish plugins set on vanished players
     */
    public static final String METADATA = "vanished";

    private VanishSupport() {
    }

    // metadata is deprecated by Paper, but it is still how vanish plugins tell other plugins
    @SuppressWarnings("deprecation")
    public static boolean isVanished(@NotNull Player player) {
        for (MetadataValue value : player.getMetadata(METADATA)) {
            if (value.asBoolean()) return true;
        }
        return false;
    }

    /**
     * @return false if the player is vanished by a plugin that can't make him visible for the game
     */
    public static boolean canPlay(@NotNull Player player) {
        return !isVanished(player) || SurvivalGames.getInstance().getEssentials().isVanished(player);
    }

}
