package com.christian34.survivalgames.modules;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Admins build anywhere (/sg build): creative mode, an empty inventory and none of the lobby protections. The
 * inventory and game mode come back when they leave the build mode (or the server).
 *
 * @author Christian34
 */
public class BuildMode implements Listener {
    private final Map<UUID, Saved> builders = new HashMap<>();

    private record Saved(ItemStack[] contents, GameMode gameMode) {
    }

    public boolean isBuilding(Player player) {
        return builders.containsKey(player.getUniqueId());
    }

    /**
     * @return true if the player builds now
     */
    public boolean toggle(Player player) {
        Saved saved = builders.remove(player.getUniqueId());
        if (saved != null) {
            restore(player, saved);
            return false;
        }
        builders.put(player.getUniqueId(), new Saved(player.getInventory().getContents(), player.getGameMode()));
        player.getInventory().clear();
        player.setGameMode(GameMode.CREATIVE);
        return true;
    }

    private static void restore(Player player, Saved saved) {
        player.getInventory().setContents(saved.contents());
        player.setGameMode(saved.gameMode());
    }

    /**
     * the plugin is disabled
     */
    public void restoreAll() {
        for (Map.Entry<UUID, Saved> entry : builders.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) restore(player, entry.getValue());
        }
        builders.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Saved saved = builders.remove(e.getPlayer().getUniqueId());
        if (saved != null) restore(e.getPlayer(), saved);
    }

}
