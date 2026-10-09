package com.christian34.survivalgames.listeners;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.user.GamePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Players teleported out of the world of their game or of the map they edit (e.g. by another plugin) leave it and
 * get their things back.
 *
 * @author Christian34
 */
public class TeleportListener implements Listener {
    private final SurvivalGames plugin;

    public TeleportListener(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        World to = e.getTo().getWorld();
        Player player = e.getPlayer();
        if (to.equals(e.getFrom().getWorld())) return;
        GamePlayer gamePlayer = plugin.getUser(player);
        Game game = gamePlayer.getGame();
        if (game != null && !to.equals(game.getGameWorld().getWorld())) {
            game.leave(gamePlayer, Game.LeaveReason.COMMAND);
            return;
        }
        Arena edited = plugin.getArenaManager().getEditedArena(player);
        if (edited != null && !to.equals(plugin.getArenaManager().getEditWorld(edited))) {
            plugin.getArenaManager().stopEditing(gamePlayer);
        }
    }

}
