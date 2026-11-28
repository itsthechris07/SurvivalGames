package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.UsageStats;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.lib.lang.LangText;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Supply drops: every few minutes of a running game a chest with the supply loot of loot.yml lands at a random place
 * near the center. Its coordinates are announced a few seconds before, a column of particles marks the place. Not
 * during the deathmatch.
 *
 * @author Christian34
 */
public class SupplyDrop extends GameModule {
    public static final String MODULE = "supply-drop";
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
        super(plugin, MODULE);
    }

    /**
     * @return true if the chest is a supply drop (it keeps its loot)
     */
    public boolean isDrop(Block block) {
        return drops.contains(block);
    }

    /**
     * the drop that is announced and hasn't landed yet is kept in the game
     */
    @Override
    public void tick(Game game) {
        if (game.isFinalPhase()) {
            game.setModuleData(this, null);
            return;
        }
        Pending drop = game.getModuleData(this, Pending.class);
        if (drop != null) {
            if (--drop.seconds <= 0) {
                game.setModuleData(this, null);
                land(game, drop.location);
            } else {
                showMarker(drop.location);
            }
        }
        int interval = Math.max(1, config().getInt("minutes")) * 60;
        if (game.getRunningSeconds() % interval == 0 && game.getModuleData(this, Pending.class) == null) announce(game);
    }

    @Override
    public void stop(Game game) {
        // the chests that have landed in games that are over
        drops.removeIf(block -> {
            Game other = plugin.getGameManager().getGame(block.getWorld());
            return other == null || other == game;
        });
    }

    /**
     * a drop is coming: its place is announced
     */
    public void announce(Game game) {
        Location location = findLocation(game);
        if (location == null) return;
        int warning = Math.max(1, config().getInt("warning"));
        game.setModuleData(this, new Pending(location, warning));
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
        double radius = Math.max(1, config().getInt("radius"));
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
        plugin.getReplays().block(block);
        block.setType(Material.CHEST);
        if (block.getState() instanceof Chest chest) plugin.getLoot().fillSupply(chest.getBlockInventory());
        drops.add(block);
        plugin.getUsageStats().used(UsageStats.Feature.SUPPLY_DROP);
        World world = location.getWorld();
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2, 1);
        world.spawnParticle(Particle.EXPLOSION, location.clone().add(0.5, 0.5, 0.5), 3);
        game.broadcast(LangText.SUPPLY_DROP_LANDED, location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

}
