package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.files.ConfigFile;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.lib.lang.LangText;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Supply drops: every few minutes of a running game a chest with the supply loot of loot.yml lands at a random place
 * near the center. Its coordinates are announced a few seconds before, a column of particles marks the place. Not
 * during the deathmatch.
 *
 * @author Christian34
 */
public class SupplyDrop {
    public static final String MODULE = "supply-drop";
    private final SurvivalGames plugin;
    /**
     * seconds every game has been running
     */
    private final Map<Game, Integer> seconds = new HashMap<>();
    /**
     * the drops that are announced and haven't landed yet (where they land, seconds until then)
     */
    private final Map<Game, Pending> pending = new HashMap<>();
    /**
     * the chests that have landed (they keep their loot)
     */
    private final Set<Block> drops = new HashSet<>();

    private static final class Pending {
        private final Location location;
        private int seconds;

        private Pending(Location location, int seconds) {
            this.location = location;
            this.seconds = seconds;
        }
    }

    public SupplyDrop(SurvivalGames plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private ConfigFile config() {
        return plugin.getFileManager().getConfigFile();
    }

    /**
     * @return true if the chest is a supply drop (it keeps its loot)
     */
    public boolean isDrop(Block block) {
        return drops.contains(block);
    }

    private void tick() {
        // games that are over
        seconds.keySet().removeIf(game -> game.isStopped() || game.getState() != GameState.RUNNING);
        pending.keySet().removeIf(game -> game.isStopped() || game.getState() != GameState.RUNNING);
        drops.removeIf(block -> plugin.getGameManager().getGame(block.getWorld()) == null);
        for (Game game : plugin.getGameManager().getGames()) {
            if (game.getState() != GameState.RUNNING || !plugin.isModuleEnabled(MODULE, game)) continue;
            if (game instanceof SurvivalGame sg && sg.isDeathmatch()) {
                pending.remove(game);
                continue;
            }
            Pending drop = pending.get(game);
            if (drop != null) {
                if (--drop.seconds <= 0) {
                    pending.remove(game);
                    land(game, drop.location);
                } else {
                    showMarker(drop.location);
                }
            }
            int running = seconds.merge(game, 1, Integer::sum);
            int interval = Math.max(1, config().getInt("modules.supply-drop.minutes")) * 60;
            if (running % interval == 0 && !pending.containsKey(game)) announce(game);
        }
    }

    /**
     * a drop is coming: its place is announced
     */
    public void announce(Game game) {
        Location location = findLocation(game);
        if (location == null) return;
        int warning = Math.max(1, config().getInt("modules.supply-drop.warning"));
        pending.put(game, new Pending(location, warning));
        game.broadcast(LangText.SUPPLY_DROP_INCOMING, location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                warning);
        for (var member : game.getMembers()) {
            member.getPlayer().playSound(member.getPlayer(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
        }
    }

    /**
     * @return a random place on the ground near the center and inside the world border (not in water or lava), null
     * if there is none
     */
    @Nullable
    private Location findLocation(Game game) {
        World world = game.getWorld();
        Location center = game.getArena().getCenter(world);
        double radius = Math.max(1, config().getInt("modules.supply-drop.radius"));
        double border = world.getWorldBorder().getSize() / 2 - 3;
        if (border > 0) radius = Math.min(radius, border);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble(2 * Math.PI);
            // evenly over the circle
            double distance = radius * Math.sqrt(random.nextDouble());
            int x = (int) Math.floor(center.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(center.getZ() + Math.sin(angle) * distance);
            Block ground = world.getHighestBlockAt(x, z);
            if (ground.isLiquid() || ground.getY() <= world.getMinHeight() || ground.getY() + 1 >= world.getMaxHeight()) {
                continue;
            }
            return ground.getRelative(0, 1, 0).getLocation();
        }
        return null;
    }

    private static void showMarker(Location location) {
        World world = location.getWorld();
        Location top = location.clone().add(0.5, 0, 0.5);
        for (int y = 0; y < 30; y += 2) {
            world.spawnParticle(Particle.FIREWORK, top.clone().add(0, y, 0), 2, 0.1, 0.5, 0.1, 0);
        }
    }

    /**
     * the chest lands with the supply loot
     */
    private void land(Game game, Location location) {
        Block block = location.getBlock();
        block.setType(Material.CHEST);
        if (block.getState() instanceof Chest chest) plugin.getLoot().fillSupply(chest.getBlockInventory());
        drops.add(block);
        World world = location.getWorld();
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2, 1);
        world.spawnParticle(Particle.EXPLOSION, location.clone().add(0.5, 0.5, 0.5), 3);
        game.broadcast(LangText.SUPPLY_DROP_LANDED, location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

}
