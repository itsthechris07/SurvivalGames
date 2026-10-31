package com.christian34.survivalgames.listeners;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.game.VaroGame;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.user.GamePlayer;
import com.christian34.survivalgames.user.UserStorage;
import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * Joining and leaving the server, dying and fighting in a game, the chat of the games with team chat.
 *
 * @author Christian34
 */
public class PlayerListener implements Listener {
    private final SurvivalGames plugin;
    /**
     * where the players died (they watch from there)
     */
    private final Map<UUID, Location> deaths = new HashMap<>();

    public PlayerListener(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    @Nullable
    private Game getGame(Player player) {
        return plugin.getUser(player).getGame();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        // the server crashed while the player was in a game (on a game server there is nothing to restore)
        if (!plugin.getNetworkManager().isGameServer()) UserStorage.restoreBackupFile(player);
        plugin.updateVisibility();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        GamePlayer gamePlayer = plugin.getUser(player);
        Game game = gamePlayer.getGame();
        if (game != null) game.leave(gamePlayer, Game.LeaveReason.QUIT);
        if (plugin.getArenaManager().isEditing(player)) plugin.getArenaManager().stopEditing(gamePlayer);
        plugin.getSidebar().remove(player);
        plugin.getDuels().forget(player.getUniqueId());
        plugin.removeUser(player);
        deaths.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player player = e.getPlayer();
        GamePlayer gamePlayer = plugin.getUser(player);
        Game game = gamePlayer.getGame();
        if (game == null || gamePlayer.isSpectator()) return;
        deaths.put(player.getUniqueId(), player.getLocation());
        e.setKeepInventory(false);
        game.onDeath(gamePlayer, e);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        GamePlayer gamePlayer = plugin.getUser(player);
        Game game = gamePlayer.getGame();
        Location death = deaths.remove(player.getUniqueId());
        if (game == null) return;
        Location location = death != null && death.getWorld().equals(game.getWorld())
                && death.getY() > game.getWorld().getMinHeight() ? death : game.getLobby();
        e.setRespawnLocation(location);
        // the game mode can't change while respawning
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) game.afterRespawn(gamePlayer, location);
        });
    }

    /**
     * nobody gets hurt outside of the running game, spectators never, players who just joined a Varo are protected
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        GamePlayer gamePlayer = plugin.getUser(player);
        Game game = gamePlayer.getGame();
        if (game == null) return;
        if (gamePlayer.isSpectator() || game.getState() != GameState.RUNNING) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                player.teleport(gamePlayer.isSpectator() ? game.getArena().getCenter(game.getWorld()) : game.getLobby());
            }
            return;
        }
        Participant participant = game.getParticipant(player.getUniqueId());
        if (participant != null && participant.isProtected()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = getAttacker(e.getDamager());
        if (attacker == null || attacker == victim) return;
        Game game = getGame(victim);
        GamePlayer attackerPlayer = plugin.getUser(attacker);
        if (game == null) {
            // players in a game can't hurt anybody outside of it
            if (attackerPlayer.getGame() != null) e.setCancelled(true);
            return;
        }
        if (attackerPlayer.getGame() != game || attackerPlayer.isSpectator() || game.getState() != GameState.RUNNING) {
            e.setCancelled(true);
            return;
        }
        boolean peaceful = game instanceof SurvivalGame sg ? sg.isGracePeriod() : game instanceof VaroGame varo && varo.isPeaceful();
        if (peaceful) {
            e.setCancelled(true);
            return;
        }
        GameTeam team = game.getTeam(attacker.getUniqueId());
        if (team != null && team.isMember(victim.getUniqueId())
                && !game.getArena().settings().getBoolean("friendly-fire")) {
            e.setCancelled(true);
            return;
        }
        // who attacks isn't protected anymore
        Participant participant = game.getParticipant(attacker.getUniqueId());
        if (participant != null && participant.isProtected()) participant.protect(0);
    }

    @Nullable
    public static Player getAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    /**
     * players in a game don't make progress in the advancements (advancements of the game type) - they belong to the
     * normal worlds
     */
    @EventHandler(ignoreCancelled = true)
    public void onAdvancement(PlayerAdvancementCriterionGrantEvent e) {
        Game game = getGame(e.getPlayer());
        if (game != null && !game.getArena().settings().getBoolean("advancements")) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        GamePlayer gamePlayer = plugin.getUser(player);
        Game game = gamePlayer.getGame();
        if (game != null && (gamePlayer.isSpectator() || game.getState() != GameState.RUNNING)) e.setCancelled(true);
    }

    /**
     * the players wait on their spawn points until the countdown is over (looking around is fine)
     */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedPosition()) return;
        GamePlayer gamePlayer = plugin.getUser(e.getPlayer());
        Game game = gamePlayer.getGame();
        if (game == null || game.getState() != GameState.STARTING || gamePlayer.isSpectator()) return;
        Location to = e.getFrom().clone();
        to.setYaw(e.getTo().getYaw());
        to.setPitch(e.getTo().getPitch());
        e.setTo(to);
    }

    /**
     * [Team] Name: message - only the players of the same game see it (chat.separate-games), messages starting with
     * chat.team-prefix only go to the own team
     * - HIGHEST and loaded after EasyPrefix: the game format wins over chat plugins (chat.format)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player player = e.getPlayer();
        GamePlayer gamePlayer = plugin.getUser(player);
        Game game = gamePlayer.getGame();
        boolean separate = plugin.getFileManager().getConfigFile().getBoolean("chat.separate-games");
        if (separate) {
            // the console still sees everything
            e.viewers().removeIf(viewer -> viewer instanceof Player other && plugin.getUser(other).getGame() != game);
        }
        if (game == null) return;
        GameTeam team = gamePlayer.isSpectator() ? null : game.getTeam(player.getUniqueId());
        String teamPrefix = plugin.getFileManager().getConfigFile().getString("chat.team-prefix");
        String text = I.plain(e.message());
        if (team != null && game.getArena().getTeamSize() > 1 && teamPrefix != null && !teamPrefix.isEmpty()
                && text.startsWith(teamPrefix) && text.length() > teamPrefix.length()) {
            e.message(Component.text(text.substring(teamPrefix.length()).trim()));
            e.viewers().removeIf(viewer -> viewer instanceof Player other && !team.isMember(other.getUniqueId()));
            e.renderer(ChatRenderer.viewerUnaware((source, name, message) ->
                    i18n(LangText.CHAT_TEAM, team.getDisplayName(), source.getName(), message)));
            return;
        }
        if (gamePlayer.isSpectator()) {
            // spectators only talk to each other
            e.viewers().removeIf(viewer -> viewer instanceof Player other && !plugin.getUser(other).isSpectator());
            e.renderer(ChatRenderer.viewerUnaware((source, name, message) ->
                    i18n(LangText.CHAT_SPECTATOR, source.getName(), message)));
            return;
        }
        if (!plugin.getFileManager().getConfigFile().getBoolean("chat.format")) return;
        e.renderer(ChatRenderer.viewerUnaware((source, name, message) -> team == null
                ? i18n(LangText.CHAT_NO_TEAM, source.getName(), message)
                : i18n(LangText.CHAT, team.getDisplayName(), source.getName(), message)));
    }

}
