package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.UsageStats;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.user.GamePlayer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The force field (modules.force-field): the world border of a game throws back what hits it - projectiles (arrows,
 * tridents, snowballs, ender pearls, ...) bounce off and fly back, players who run into it get a shock and are
 * pushed back into the arena.
 *
 * @author Christian34
 */
public class ForceField extends GameModule implements Listener {
    public static final String MODULE = "force-field";
    /**
     * the projectiles flying in games with a force field
     */
    private final Set<Projectile> projectiles = ConcurrentHashMap.newKeySet();

    public ForceField(SurvivalGames plugin) {
        super(plugin, MODULE);
        Bukkit.getScheduler().runTaskTimer(plugin, this::update, 1L, 1L);
    }

    @Nullable
    private Game getGame(Entity entity) {
        Game game = plugin.getGameManager().getGame(entity.getWorld());
        return game != null && game.getState() == GameState.RUNNING && isEnabled(game) ? game : null;
    }

    private static boolean hasBorder(Game game) {
        return game.getBorderSize() > 0;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        Game game = getGame(e.getEntity());
        if (game != null && hasBorder(game)) projectiles.add(e.getEntity());
    }

    private void update() {
        for (Iterator<Projectile> iterator = projectiles.iterator(); iterator.hasNext(); ) {
            Projectile projectile = iterator.next();
            Game game = projectile.isValid() ? getGame(projectile) : null;
            if (game == null || projectile.isOnGround()) {
                iterator.remove();
            } else if (hasBorder(game)) {
                reflect(projectile);
            }
        }
    }

    /**
     * the projectile bounces off the world border if it is about to leave it
     *
     * @return true if it has bounced
     */
    public boolean reflect(Projectile projectile) {
        WorldBorder border = projectile.getWorld().getWorldBorder();
        Location location = projectile.getLocation();
        Vector velocity = projectile.getVelocity();
        Location next = location.clone().add(velocity);
        double radius = border.getSize() / 2;
        Location center = border.getCenter();
        boolean x = Math.abs(next.getX() - center.getX()) >= radius && Math.signum(velocity.getX())
                == Math.signum(next.getX() - center.getX());
        boolean z = Math.abs(next.getZ() - center.getZ()) >= radius && Math.signum(velocity.getZ())
                == Math.signum(next.getZ() - center.getZ());
        if (!x && !z) return false;
        if (x) velocity.setX(-velocity.getX());
        if (z) velocity.setZ(-velocity.getZ());
        projectile.setVelocity(velocity);
        effect(location);
        plugin.getUsageStats().used(UsageStats.Feature.FORCE_FIELD);
        return true;
    }

    /**
     * players who run into the world border are pushed back with a shock
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedPosition()) return;
        Player player = e.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) return;
        GamePlayer gamePlayer = plugin.getUser(player);
        Game game = gamePlayer.getGame();
        if (game == null || gamePlayer.isSpectator() || game.getState() != GameState.RUNNING
                || !isEnabled(game) || !hasBorder(game)) return;
        WorldBorder border = player.getWorld().getWorldBorder();
        if (!isInside(border, e.getFrom()) || isInside(border, e.getTo())) return;
        e.setTo(e.getFrom());
        Vector back = border.getCenter().toVector().subtract(e.getFrom().toVector()).setY(0);
        if (back.lengthSquared() > 0) player.setVelocity(back.normalize().multiply(0.8).setY(0.3));
        double damage = config().getDouble("damage");
        if (damage > 0) player.damage(damage);
        effect(e.getFrom());
    }

    /**
     * WorldBorder#isInside of MockBukkit takes the size as the radius
     */
    private static boolean isInside(WorldBorder border, Location location) {
        double radius = border.getSize() / 2;
        Location center = border.getCenter();
        return Math.abs(location.getX() - center.getX()) < radius && Math.abs(location.getZ() - center.getZ()) < radius;
    }

    private static void effect(Location location) {
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 2f);
        try {
            location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location, 20, 0.3, 0.3, 0.3, 0.1);
        } catch (RuntimeException ignored) {
            // MockBukkit
        }
    }

}
