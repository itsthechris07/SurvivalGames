package com.christian34.survivalgames.arena;

import com.christian34.survivalgames.Debug;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * Generates the vanilla world (all steps are left to the server), only the spawn is chosen by the plugin.
 * <p>
 * Without it the server searches a spawn point when a world is created: it generates chunks around 0,0 on the server
 * thread until it finds a suitable block, which blocks the server for a few seconds (a new UHC game). Here the spawn
 * is chosen by the biomes (they come from the noise, no chunk is generated): the land closest to 0,0. Its height is
 * set once the chunk has been generated in the background ({@link #fixHeight(World)}).
 *
 * @author Christian34
 */
class VanillaGenerator extends ChunkGenerator {
    /**
     * the distance between the biomes that are checked, and how far from 0,0 the spawn may be
     */
    private static final int STEP = 32;
    private static final int MAX_DISTANCE = 1024;
    @Nullable
    private Location spawn;

    @Override
    public boolean shouldGenerateNoise() {
        return true;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return true;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return true;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return true;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return true;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return true;
    }

    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        int y = world.getSeaLevel() + 1;
        int[] land = findLand(world);
        this.spawn = new Location(world, land[0] + 0.5, y, land[1] + 0.5);
        return spawn.clone();
    }

    /**
     * @return x and z of the land closest to 0,0 (0,0 if there is only water)
     */
    private static int[] findLand(WorldInfo info) {
        if (!(info instanceof World world)) return new int[]{0, 0};
        try {
            int y = world.getSeaLevel();
            for (int distance = 0; distance <= MAX_DISTANCE; distance += STEP) {
                // the ring around 0,0 at this distance
                for (int x = -distance; x <= distance; x += STEP) {
                    for (int z = -distance; z <= distance; z += STEP) {
                        if (Math.max(Math.abs(x), Math.abs(z)) != distance) continue;
                        if (isLand(world.getBiome(x, y, z))) return new int[]{x, z};
                    }
                }
            }
        } catch (RuntimeException exception) {
            Debug.info("Couldn't check the biomes of world " + world.getName() + ": " + exception);
        }
        return new int[]{0, 0};
    }

    static boolean isLand(Biome biome) {
        String key = biome.getKey().getKey();
        return !key.contains("ocean") && !key.contains("river");
    }

    /**
     * sets the height of the spawn (the server used sea level) once its chunk has been generated in the background
     */
    void fixHeight(World world) {
        if (spawn == null) return;
        Location location = spawn;
        world.getChunkAtAsync(location.getBlockX() >> 4, location.getBlockZ() >> 4).thenAccept(chunk -> {
            // not on a tree
            int y = world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ(),
                    HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            world.setSpawnLocation(location.getBlockX(), y, location.getBlockZ());
        }).exceptionally(exception -> {
            Debug.info("Couldn't set the spawn of world " + world.getName() + ": " + exception);
            return null;
        });
    }

}
