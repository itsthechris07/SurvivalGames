package com.christian34.survivalgames.arena;

import com.christian34.survivalgames.PluginTestBase;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The spawn of a generated world is chosen by the plugin (the search of the server blocks it for seconds).
 *
 * @author Christian34
 */
class VanillaGeneratorTest extends PluginTestBase {
    @TempDir
    File temp;

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

    @Test
    void theCopyOfAMapSpawnsWhereTheMapDoes() throws IOException {
        World world = server.addSimpleWorld("copy");
        Location spawn = VanillaGenerator.at(new BlockVector(-209, 45, 67)).getFixedSpawnLocation(world, new Random());
        assertNotNull(spawn);
        assertEquals(-209, spawn.getBlockX());
        assertEquals(45, spawn.getBlockY());
        assertEquals(67, spawn.getBlockZ());
        // a map without level.dat: 0,0 - no chunk is loaded for its height until the spawn is needed
        spawn = VanillaGenerator.at(null).getFixedSpawnLocation(world, new Random());
        assertNotNull(spawn);
        assertEquals(0, spawn.getBlockX());
        assertEquals(world.getMinHeight(), spawn.getBlockY());
    }

    @Test
    void theHeightOfTheSpawnIsFoundOnceItIsNeeded() {
        World world = server.addSimpleWorld("unknown");
        world.setSpawnLocation(3, world.getMinHeight(), 5);
        world.getBlockAt(3, 80, 5).setType(org.bukkit.Material.STONE);
        Location spawn = GameWorld.getSpawn(world);
        assertEquals(81, spawn.getBlockY());
        assertEquals(3, spawn.getBlockX());
        assertEquals(81, world.getSpawnLocation().getBlockY());
        // a known spawn stays as it is
        world.setSpawnLocation(3, 40, 5);
        assertEquals(40, GameWorld.getSpawn(world).getBlockY());
    }

    @Test
    void readsTheSpawnOfAMap() throws IOException {
        File map = new File(temp, "map");
        assertTrue(map.mkdirs());
        assertNull(GameWorld.readSpawn(map));
        // since 1.21.9
        levelDat(map, out -> {
            out.writeByte(10);
            out.writeUTF("spawn");
            out.writeByte(11);
            out.writeUTF("pos");
            out.writeInt(3);
            out.writeInt(-209);
            out.writeInt(45);
            out.writeInt(67);
            out.writeByte(0);
        });
        assertEquals(new BlockVector(-209, 45, 67), GameWorld.readSpawn(map));
        // before
        levelDat(map, out -> {
            for (String axis : new String[]{"X", "Y", "Z"}) {
                out.writeByte(3);
                out.writeUTF("Spawn" + axis);
                out.writeInt(axis.equals("Y") ? 70 : 12);
            }
        });
        assertEquals(new BlockVector(12, 70, 12), GameWorld.readSpawn(map));
    }

    @Test
    void aCopyOfAMapDoesntLetTheServerSearchASpawn() throws IOException {
        File map = new File(temp, "valley");
        assertTrue(new File(map, "region").mkdirs());
        // a Varo: its world is kept - after the copy in the background it exists already, but is new for the server
        GameWorld varo = new GameWorld(map, null, "valley", true);
        varo.prepare().join();
        assertTrue(varo.exists());
        assertInstanceOf(VanillaGenerator.class, varo.generator());
        // after a restart the server knows its spawn
        assertNull(new GameWorld(map, null, "valley", true).generator());
        assertInstanceOf(VanillaGenerator.class, new GameWorld(map, null, "round", false).generator());
    }

    private interface Tags {
        void write(DataOutputStream out) throws IOException;
    }

    private static void levelDat(File map, Tags tags) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(
                Files.newOutputStream(new File(map, "level.dat").toPath())))) {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(10);
            out.writeUTF("Data");
            tags.write(out);
            out.writeByte(0);
            out.writeByte(0);
        }
    }

}
