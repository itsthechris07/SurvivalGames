package com.christian34.survivalgames.arena;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.files.FileJobs;
import com.christian34.survivalgames.files.FileManager;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The maps: copied in the background, the warning about big ones and /sg map trim.
 *
 * @author Christian34
 */
class MapsTest extends PluginTestBase {
    @TempDir
    File temp;
    private PlayerMock admin;

    @BeforeEach
    void setUpAdmin() {
        admin = addAdmin("Admin");
    }

    // ---------------------------------------------------------------- region files

    private static byte[] nbt(Map<String, Object> root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(2);
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            out.writeByte(10);
            out.writeUTF("");
            writeCompound(out, root);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static void writeCompound(DataOutputStream out, Map<String, Object> compound) throws IOException {
        for (Map.Entry<String, Object> entry : compound.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text) {
                out.writeByte(8);
                out.writeUTF(entry.getKey());
                out.writeUTF(text);
            } else if (value instanceof Integer number) {
                out.writeByte(3);
                out.writeUTF(entry.getKey());
                out.writeInt(number);
            } else if (value instanceof long[] array) {
                out.writeByte(12);
                out.writeUTF(entry.getKey());
                out.writeInt(array.length);
                for (long element : array) {
                    out.writeLong(element);
                }
            } else if (value instanceof Map<?, ?> map) {
                out.writeByte(10);
                out.writeUTF(entry.getKey());
                writeCompound(out, (Map<String, Object>) map);
            } else if (value instanceof List<?> list) {
                out.writeByte(9);
                out.writeUTF(entry.getKey());
                out.writeByte(list.isEmpty() ? 0 : 10);
                out.writeInt(list.size());
                for (Object element : list) {
                    writeCompound(out, (Map<String, Object>) element);
                }
            }
        }
        out.writeByte(0);
    }

    /**
     * a chunk with one section of these blocks
     */
    private static RegionFile.Chunk chunk(String... blocks) throws IOException {
        return withStatus("minecraft:full", blocks);
    }

    private static RegionFile.Chunk withStatus(String status, String... blocks) throws IOException {
        List<Object> palette = new ArrayList<>();
        for (String block : blocks) {
            palette.add(Map.of("Name", block));
        }
        Map<String, Object> states = new HashMap<>();
        states.put("palette", palette);
        if (blocks.length > 1) states.put("data", new long[256]);
        Map<String, Object> root = new HashMap<>();
        root.put("DataVersion", 4000);
        root.put("Status", status);
        root.put("sections", List.of(Map.of("block_states", states), Map.of("biomes", Map.of())));
        root.put("block_entities", List.of());
        return new RegionFile.Chunk(nbt(root), 1);
    }

    private static RegionFile.Chunk entities(int count) throws IOException {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(Map.of("id", "minecraft:armor_stand"));
        }
        return new RegionFile.Chunk(nbt(Map.of("Entities", list)), 1);
    }

    private static File region(File map, String folder, int x, int z) {
        return new File(map, folder + "/r." + x + "." + z + ".mca");
    }

    private static void write(File file, Map<Integer, RegionFile.Chunk> chunks) throws IOException {
        assertTrue(file.getParentFile().mkdirs() || file.getParentFile().isDirectory());
        RegionFile.write(file, chunks);
    }

    @Test
    void readsAndWritesRegionFiles() throws IOException {
        File file = region(temp, "region", 0, 0);
        RegionFile.Chunk stone = chunk("minecraft:stone", "minecraft:air");
        write(file, Map.of(0, stone, 1023, chunk("minecraft:air")));
        Map<Integer, RegionFile.Chunk> read = RegionFile.read(file);
        assertEquals(Set.of(0, 1023), read.keySet());
        assertArrayEquals(stone.payload(), read.get(0).payload());
        Map<String, Object> nbt = RegionFile.readNbt(read.get(0));
        assertNotNull(nbt);
        assertEquals("minecraft:full", nbt.get("Status"));
        // an empty region is deleted
        write(file, Map.of());
        assertFalse(file.exists());
    }

    @Test
    void emptyChunks() throws IOException {
        assertTrue(MapTrimmer.isEmpty(chunk("minecraft:air"), null));
        assertTrue(MapTrimmer.isEmpty(chunk("minecraft:air"), entities(0)));
        assertFalse(MapTrimmer.isEmpty(chunk("minecraft:air", "minecraft:stone"), null));
        // an armor stand, a hologram, an NPC
        assertFalse(MapTrimmer.isEmpty(chunk("minecraft:air"), entities(1)));
        // something it can't read
        assertFalse(MapTrimmer.isEmpty(new RegionFile.Chunk(new byte[]{4, 1, 2, 3}, 1), null));
        // generation has only begun: never sent to the players, the server finishes it again
        assertEquals(MapTrimmer.Kind.UNFINISHED,
                MapTrimmer.classify(withStatus("minecraft:features", "minecraft:stone", "minecraft:air"), null));
        assertEquals(MapTrimmer.Kind.KEEP, MapTrimmer.classify(withStatus("minecraft:features", "minecraft:air"),
                entities(1)));
    }

    @Test
    void trimRemovesEmptyChunks() throws IOException {
        File map = new File(temp, "void");
        write(region(map, "region", 0, 0), Map.of(0, chunk("minecraft:air"), 1,
                chunk("minecraft:stone", "minecraft:air"), 2, chunk("minecraft:air")));
        write(region(map, "entities", 0, 0), Map.of(2, entities(1)));
        write(region(map, "region", 1, 0), Map.of(5, chunk("minecraft:air")));

        MapTrimmer.Result result = MapTrimmer.analyse(map, null);
        assertEquals(4, result.chunks());
        assertEquals(2, result.empty());
        assertEquals(0, result.outside());
        assertTrue(result.removed() > 0);
        // nothing has changed
        assertEquals(3, RegionFile.read(region(map, "region", 0, 0)).size());
        assertFalse(MapTrimmer.isVoid(map));

        File backup = new File(temp, "backups/void.zip");
        MapTrimmer.trim(map, null, backup);
        assertEquals(Set.of(1, 2), RegionFile.read(region(map, "region", 0, 0)).keySet());
        assertFalse(region(map, "region", 1, 0).exists());
        assertTrue(backup.isFile());
        // the removed chunks stay empty in the copies
        assertTrue(MapTrimmer.isVoid(map));
        assertEquals(0, MapTrimmer.analyse(map, null).total());
    }

    @Test
    void trimRemovesChunksOutsideTheBorder() throws IOException {
        File map = new File(temp, "island");
        write(region(map, "region", 0, 0), Map.of(0, chunk("minecraft:stone", "minecraft:air")));
        write(region(map, "region", 5, 5), Map.of(0, chunk("minecraft:stone", "minecraft:air")));
        write(region(map, "entities", 5, 5), Map.of(0, entities(2)));
        write(region(map, "poi", 5, 5), Map.of(0, entities(0)));

        List<MapTrimmer.Area> areas = List.of(MapTrimmer.Area.around(0, 0, 100, 2));
        MapTrimmer.Result result = MapTrimmer.trim(map, areas, new File(temp, "island.zip"));
        assertEquals(1, result.outside());
        assertEquals(0, result.empty());
        assertTrue(region(map, "region", 0, 0).isFile());
        for (String folder : List.of("region", "entities", "poi")) {
            assertFalse(region(map, folder, 5, 5).exists(), folder);
        }
        // no empty chunk was removed: the server still generates what isn't in the map
        assertFalse(MapTrimmer.isVoid(map));
    }

    @Test
    void unfinishedChunksDontMakeAVoidMap() throws IOException {
        File map = new File(temp, "valley");
        write(region(map, "region", 0, 0), Map.of(0, chunk("minecraft:stone", "minecraft:air"),
                1, withStatus("minecraft:structure_starts", "minecraft:air"),
                2, withStatus("minecraft:noise", "minecraft:stone", "minecraft:air")));
        MapTrimmer.Result result = MapTrimmer.trim(map, null, new File(temp, "valley.zip"));
        assertEquals(2, result.empty());
        assertEquals(Set.of(0), RegionFile.read(region(map, "region", 0, 0)).keySet());
        // the terrain next to the map is generated as before
        assertFalse(MapTrimmer.isVoid(map));
    }

    // ---------------------------------------------------------------- the commands

    @Test
    void trimCommand() throws IOException {
        Arena arena = createArena("castle", GameType.SURVIVAL_GAMES, 4);
        arena.setBorderSize(100);
        File map = new File(FileManager.getMapsFolder(), "castle");
        write(region(map, "region", 0, 0), Map.of(0, chunk("minecraft:stone", "minecraft:air"),
                1, chunk("minecraft:air")));
        write(region(map, "region", 20, 20), Map.of(0, chunk("minecraft:stone", "minecraft:air")));

        execute(admin, "sg map list");
        assertContains(messages(admin), "castle: 0 MB, arenas castle");

        execute(admin, "sg map trim castle");
        List<String> messages = messages(admin);
        assertContains(messages, "Removable: 1 empty or unfinished chunks and 1 chunks outside the world border");
        assertContains(messages, "[Trim]");
        assertTrue(region(map, "region", 20, 20).isFile());

        execute(admin, "sg map trim castle --confirm");
        assertContains(messages(admin), "Map castle has been trimmed: 2 chunks removed");
        assertFalse(region(map, "region", 20, 20).exists());
        File[] backups = new File(FileManager.getPluginFolder(), "backups/maps").listFiles();
        assertNotNull(backups);
        assertEquals(1, backups.length);
        // the copies of the games: empty where the map has no chunks
        assertTrue(MapTrimmer.isVoid(map));

        execute(admin, "sg map trim castle");
        assertContains(messages(admin), "has nothing to remove");
        execute(admin, "sg map trim nope");
        assertContains(messages(admin), "There is no map nope in maps/");
    }

    @Test
    void theLobbyWorldKeepsItsChunks() throws IOException {
        createMap("hub");
        execute(admin, "sg lobby setworld hub");
        messages(admin);
        File map = new File(FileManager.getMapsFolder(), "hub");
        write(region(map, "region", 40, 40), Map.of(0, chunk("minecraft:stone", "minecraft:air")));
        // no border: everything is used
        assertNull(plugin.getArenaManager().getUsedAreas(map));
        execute(admin, "sg map trim hub");
        assertContains(messages(admin), "has nothing to remove");
    }

    @Test
    void warnsAboutBigMaps() throws IOException {
        createArena("big", GameType.SURVIVAL_GAMES, 4);
        execute(admin, "sg arena big info");
        assertNotContains(messages(admin), "every game copies it");

        try (RandomAccessFile file = new RandomAccessFile(new File(FileManager.getMapsFolder(), "big/region.mca"),
                "rw")) {
            file.setLength(60L * 1024 * 1024);
        }
        execute(admin, "sg arena big info");
        // measured at most once a minute
        assertNotContains(messages(admin), "every game copies it");
        plugin.getArenaManager().mapChanged(new File(FileManager.getMapsFolder(), "big"));
        execute(admin, "sg arena big info");
        assertContains(messages(admin), "the map big has 60 MB, every game copies it [Make smaller]");
    }

    @Test
    void theGameOpensOnceItsMapIsCopied() {
        List<Runnable> jobs = new ArrayList<>();
        FileJobs.setExecutor(jobs::add);
        Arena arena = createArena("castle", GameType.SURVIVAL_GAMES, 4);
        execute(admin, "sg arena castle creategame");
        assertContains(messages(admin), "The map of arena castle is being copied");
        assertNull(plugin.getGameManager().getGame(arena));
        execute(admin, "sg arena castle creategame");
        assertContains(messages(admin), "is being copied");

        while (!jobs.isEmpty()) {
            jobs.removeFirst().run();
        }
        Game game = plugin.getGameManager().getGame(arena);
        assertNotNull(game);
        assertNotNull(game.getWorld());
        assertContains(messages(admin), "The game of arena castle has been created");
    }

    @Test
    void theLobbyWorldIsCopiedInTheBackground() {
        createMap("hub");
        execute(admin, "sg lobby setworld hub");
        Arena arena = createArena("castle", GameType.SURVIVAL_GAMES, 4);
        arena.removeLobby();
        Game game = plugin.getGameManager().createGame(arena);
        String lobby = "sg_lobby_" + game.getGameWorld().getWorldName().substring(3);
        assertNotNull(server.getWorld(lobby));

        // the lobby world has changed: a new copy, loaded once it has been made
        List<Runnable> jobs = new ArrayList<>();
        FileJobs.setExecutor(jobs::add);
        plugin.getArenaManager().lobbyChanged();
        assertFalse(jobs.isEmpty());
        assertNull(server.getWorld(lobby));

        while (!jobs.isEmpty()) {
            jobs.removeFirst().run();
        }
        assertNotNull(server.getWorld(lobby));
        assertEquals(lobby, game.getLobby().getWorld().getName());
    }

    @Test
    void aJoinWaitsForTheCopyOfTheLobbyWorld() throws Exception {
        createMap("hub");
        execute(admin, "sg lobby setworld hub");
        Arena arena = createArena("castle", GameType.SURVIVAL_GAMES, 4);
        arena.removeLobby();
        Game game = plugin.getGameManager().createGame(arena);
        ExecutorService thread = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            FileJobs.setExecutor(job -> thread.execute(() -> {
                started.countDown();
                assertDoesNotThrow(() -> release.await(10, TimeUnit.SECONDS));
                job.run();
            }));
            plugin.getArenaManager().lobbyChanged();
            assertTrue(started.await(10, TimeUnit.SECONDS));
            release.countDown();
            // the first player is there before the copy: he waits for it
            assertTrue(game.getLobby().getWorld().getName().startsWith("sg_lobby_"));
            // the copy is loaded once only
            server.getScheduler().performOneTick();
            assertEquals(game.getLobby().getWorld(), game.getLobby().getWorld());
        } finally {
            thread.shutdownNow();
        }
    }

}
