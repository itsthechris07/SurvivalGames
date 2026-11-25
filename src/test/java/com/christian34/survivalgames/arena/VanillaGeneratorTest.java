package com.christian34.survivalgames.arena;

import com.christian34.survivalgames.PluginTestBase;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The spawn of a generated world is chosen by the plugin (the search of the server blocks it for seconds).
 *
 * @author Christian34
 */
class VanillaGeneratorTest extends PluginTestBase {

    @Test
    void vanillaGeneration() {
        VanillaGenerator generator = new VanillaGenerator();
        assertTrue(generator.shouldGenerateNoise());
        assertTrue(generator.shouldGenerateSurface());
        assertTrue(generator.shouldGenerateCaves());
        assertTrue(generator.shouldGenerateDecorations());
        assertTrue(generator.shouldGenerateMobs());
        assertTrue(generator.shouldGenerateStructures());
        assertNull(generator.getDefaultBiomeProvider(server.addSimpleWorld("biomes")));
    }

    @Test
    void waterIsNoLand() {
        assertTrue(VanillaGenerator.isLand(Biome.PLAINS));
        assertTrue(VanillaGenerator.isLand(Biome.BEACH));
        assertFalse(VanillaGenerator.isLand(Biome.OCEAN));
        assertFalse(VanillaGenerator.isLand(Biome.DEEP_FROZEN_OCEAN));
        assertFalse(VanillaGenerator.isLand(Biome.RIVER));
    }

    @Test
    void spawnOnTheLandClosestToTheMiddle() {
        World world = server.addSimpleWorld("spawn");
        int y = world.getSeaLevel();
        for (int x = -32; x <= 32; x += 32) {
            for (int z = -32; z <= 32; z += 32) {
                world.setBiome(x, y, z, Biome.OCEAN);
            }
        }
        world.setBiome(64, y, 0, Biome.RIVER);
        Location spawn = new VanillaGenerator().getFixedSpawnLocation(world, new Random());
        assertNotNull(spawn);
        // the first land on the ring 64 blocks around 0,0
        assertEquals(-64, spawn.getBlockX());
        assertEquals(-64, spawn.getBlockZ());
        assertEquals(y + 1, spawn.getBlockY());
    }

    @Test
    void spawnInTheMiddleOnLand() {
        World world = server.addSimpleWorld("land");
        world.setBiome(0, world.getSeaLevel(), 0, Biome.PLAINS);
        Location spawn = new VanillaGenerator().getFixedSpawnLocation(world, new Random());
        assertNotNull(spawn);
        assertEquals(0, spawn.getBlockX());
        assertEquals(0, spawn.getBlockZ());
    }

}
