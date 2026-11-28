package com.christian34.survivalgames.arena;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.files.FileJobs;
import com.christian34.survivalgames.files.FileManager;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.BlockVector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The world of a game: a copy of the map of the arena (or a generated world), loaded as its own world.
 * <p>
 * Since 26.1 Paper stores every world as a dimension of the main world ({@code world/dimensions/minecraft/<key>}),
 * a folder next to the main world is ignored (without level.dat) or moved there once (with level.dat). So the map
 * is copied directly into the dimension folder and loaded by its key.
 * <p>
 * Temporary worlds (a round of Survival Games, the copy an admin edits) start with {@link #TEMP_PREFIX} and are
 * deleted when they are closed and on the next start after a crash. The world of a Varo is kept
 * ({@link #KEPT_PREFIX}) until the game is reset - it runs for days.
 * <p>
 * The map is copied off the server thread ({@link #prepare()}, a big map takes seconds), only loading the copy happens
 * on it.
 *
 * @author Christian34
 */
public class GameWorld {
    public static final String TEMP_PREFIX = "sg_";
    public static final String KEPT_PREFIX = "sgvaro_";
    /**
     * files and folders of a (pre 26.1) world save that don't belong into a dimension folder
     */
    private static final Set<String> SKIPPED_FILES = Set.of("level.dat", "level.dat_old", "uid.dat", "session.lock",
            "DIM-1", "DIM1", "dimensions", "playerdata", "players", "advancements", "stats", "datapacks",
            MapTrimmer.MARKER);
    /**
     * a copy that takes longer is reported in the console (with the hint to trim the map)
     */
    public static final long SLOW_COPY_MILLIS = 2000;
    /**
     * how long the last copy of every map took (ms) - the disk and virus scanners decide that, not the size alone
     */
    private static final Map<File, Long> COPY_MILLIS = new ConcurrentHashMap<>();
    private static final String METADATA_FILE = "data/paper/metadata.dat";
    @Nullable
    private static File dimensionsFolder;
    @Nullable
    private final File map;
    @Nullable
    private final Long seed;
    private final String worldName;
    private final boolean kept;
    private boolean prepared;
    /**
     * the copy of the map that is made off the server thread, null: not started (or the copy has been deleted)
     */
    @Nullable
    private CompletableFuture<Void> copy;
    @Nullable
    private World world;

    /**
     * @param map  the folder of the map, null: a generated world
     * @param seed the seed of a generated world (null: random)
     * @param kept true: the world stays when the game is unloaded (Varo)
     */
    public GameWorld(@Nullable File map, @Nullable Long seed, String name, boolean kept) {
        this.map = map;
        this.seed = seed;
        this.kept = kept;
        this.worldName = ((kept ? KEPT_PREFIX : TEMP_PREFIX) + name).toLowerCase(Locale.ROOT);
    }

    /**
     * the folder the worlds of the server are stored in (world/dimensions/minecraft)
     */
    public static File getDimensionsFolder() {
        if (dimensionsFolder != null) return dimensionsFolder;
        return Bukkit.getWorlds().getFirst().getWorldFolder().getParentFile();
    }

    /**
     * the folder of a loaded world: dimensions/namespace/key (like {@link World#getWorldFolder()}, which MockBukkit
     * lacks)
     */
    public static File getFolder(World world) {
        NamespacedKey key = world.getKey();
        return new File(getDimensionsFolder().getParentFile(), key.getNamespace() + "/" + key.getKey());
    }

    /**
     * only for the tests: MockBukkit doesn't know where worlds are stored
     */
    public static void setDimensionsFolder(@Nullable File folder) {
        dimensionsFolder = folder;
    }

    /**
     * @return true for the worlds of the plugin (games and the copies edited by admins)
     */
    public static boolean isPluginWorld(@Nullable World world) {
        if (world == null) return false;
        String name = world.getName();
        return name.startsWith(TEMP_PREFIX) || name.startsWith(KEPT_PREFIX);
    }

    /**
     * deletes the temporary worlds that are left over (e.g. after a crash)
     */
    public static void deleteLeftovers() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().startsWith(TEMP_PREFIX)) Bukkit.unloadWorld(world, false);
        }
        File[] folders = getDimensionsFolder().listFiles(file -> file.isDirectory() && file.getName().startsWith(TEMP_PREFIX));
        if (folders == null) return;
        for (File folder : folders) {
            Debug.info("Deleting leftover world " + folder.getName());
            FileManager.deleteDirectory(folder);
        }
    }

    /**
     * the folder with the world data of the map: the map itself, or the overworld of a world saved with 26.1+
     */
    public static File getMapData(File map) {
        File overworld = new File(map, "dimensions/minecraft/overworld");
        return overworld.isDirectory() ? overworld : map;
    }

    public File getWorldFolder() {
        return new File(getDimensionsFolder(), worldName);
    }

    /**
     * the folder of the world has been filled already (a replay: the backup of a Varo) - it is loaded as it is
     */
    public void setPrepared() {
        this.prepared = true;
    }

    /**
     * @return true if the world has been created before and is kept (a Varo after a restart)
     */
    public boolean exists() {
        return kept && getWorldFolder().isDirectory();
    }

    /**
     * @return true if the map has to be copied before the world can be loaded
     */
    private boolean needsCopy() {
        return map != null && world == null && !prepared && !exists();
    }

    /**
     * copies the map off the server thread (if the world needs a copy), {@link #load()} waits for it
     *
     * @return completes when the copy has been made (exceptionally if it failed)
     */
    public CompletableFuture<Void> prepare() {
        if (copy != null) return copy;
        if (!needsCopy()) return CompletableFuture.completedFuture(null);
        File source = map;
        File from = getMapData(source);
        File to = getWorldFolder();
        // copied under another name first: a copy that is interrupted (the server stops) isn't taken for the world of a
        // Varo, it is deleted on the next start like all temporary worlds
        File temp = new File(to.getParentFile(), TEMP_PREFIX + "copy_" + worldName);
        this.copy = FileJobs.run(() -> {
            long started = System.currentTimeMillis();
            FileManager.deleteDirectory(temp);
            if (!copyMap(from, temp)) {
                FileManager.deleteDirectory(temp);
                throw new IllegalStateException("Couldn't copy map " + source + " to " + temp);
            }
            FileManager.deleteDirectory(to);
            // renaming fails if another program (a virus scanner) has a file open right now
            if (!temp.renameTo(to) && !FileManager.copyFolder(temp, to)) {
                FileManager.deleteDirectory(temp);
                FileManager.deleteDirectory(to);
                throw new IllegalStateException("Couldn't move " + temp + " to " + to);
            }
            FileManager.deleteDirectory(temp);
            long millis = System.currentTimeMillis() - started;
            COPY_MILLIS.put(source, millis);
            if (millis >= SLOW_COPY_MILLIS) {
                Debug.warn("Copying map '" + source.getName() + "' (" + MapTrimmer.size(from) / (1024 * 1024)
                        + " MB) took " + millis / 100 / 10.0 + " s - /sg map trim " + source.getName()
                        + " can make it smaller");
            }
        }, to, source);
        return copy;
    }

    /**
     * @return how long the last copy of the map took (ms), null: it hasn't been copied since the server started
     */
    @Nullable
    public static Long getCopyMillis(File map) {
        return COPY_MILLIS.get(map);
    }

    /**
     * the map has changed (trimmed): the time of its last copy doesn't say anything anymore
     */
    public static void forgetCopyMillis(File map) {
        COPY_MILLIS.remove(map);
    }

    /**
     * loads the world: a kept world as it is, otherwise a fresh copy of the map (or a newly generated world) - waits
     * for the copy if it is made right now
     */
    public World load() {
        if (world != null) return world;
        File worldFolder = getWorldFolder();
        boolean fresh = isNew();
        if (fresh && map == null) {
            FileJobs.await(worldFolder);
            FileManager.deleteDirectory(worldFolder);
        } else if (fresh) {
            try {
                prepare().join();
            } catch (CompletionException ex) {
                this.copy = null;
                throw ex.getCause() instanceof RuntimeException cause ? cause : new IllegalStateException(ex.getCause());
            }
        }
        WorldCreator creator = new WorldCreator(NamespacedKey.minecraft(worldName));
        if (seed != null) creator.seed(seed);
        ChunkGenerator generator = generator();
        if (generator != null) creator.generator(generator);
        World created = creator.createWorld();
        if (created == null) {
            this.copy = null;
            if (!kept) FileManager.deleteDirectory(worldFolder);
            throw new IllegalStateException("Couldn't load world " + worldName + " (see the log above)");
        }
        if (generator instanceof VanillaGenerator vanilla) vanilla.fixHeight(created);
        created.setAutoSave(kept);
        this.world = created;
        return created;
    }

    /**
     * @return true if the world is new for the server: generated, or copied (also in the background, its level.dat
     * isn't copied)
     */
    private boolean isNew() {
        return copy != null || !exists() && !prepared;
    }

    /**
     * a new world gets a generator that chooses its spawn: the search of the server generates chunks on the server
     * thread and blocks it for seconds
     *
     * @return the generator of the world, null: the one of the server
     */
    @Nullable
    ChunkGenerator generator() {
        // a trimmed map: the chunks that have been removed stay empty
        if (MapTrimmer.isVoid(map)) return new VoidGenerator(readSpawn(map));
        if (!isNew()) return null;
        return map == null ? new VanillaGenerator() : VanillaGenerator.at(readSpawn(map));
    }

    /**
     * the chunks that aren't in a trimmed map: nothing but air
     */
    private static final class VoidGenerator extends ChunkGenerator {
        @Nullable
        private final BlockVector spawn;

        private VoidGenerator(@Nullable BlockVector spawn) {
            this.spawn = spawn;
        }

        @Override
        public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
            // no search for a spawn on the server thread
            if (spawn == null) return new Location(world, 0.5, unknownHeight(world), 0.5);
            return new Location(world, spawn.getBlockX() + 0.5, spawn.getBlockY(), spawn.getBlockZ() + 0.5);
        }
    }

    /**
     * @return the height of a spawn that isn't known yet (the copy of a map without level.dat): no map has its spawn at
     * the bottom of the world
     */
    static int unknownHeight(WorldInfo world) {
        return world.getMinHeight();
    }

    /**
     * the spawn of the world (if the arena or lobby has none): without level.dat in the map its height is found now -
     * only then its chunk is loaded
     */
    public static Location getSpawn(World world) {
        Location spawn = world.getSpawnLocation();
        if (spawn.getBlockY() > unknownHeight(world)) return spawn;
        try {
            int y = world.getHighestBlockYAt(spawn.getBlockX(), spawn.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
            // nothing there (a void map): the old default
            y = y <= unknownHeight(world) ? 64 : y + 1;
            world.setSpawnLocation(spawn.getBlockX(), y, spawn.getBlockZ());
        } catch (RuntimeException ex) {
            Debug.info("Couldn't find the spawn of world " + world.getName() + ": " + ex);
        }
        return world.getSpawnLocation();
    }

    /**
     * @return the spawn of the map from its level.dat (null if it has none)
     */
    @Nullable
    static BlockVector readSpawn(@Nullable File map) {
        if (map == null) return null;
        File file = new File(map, "level.dat");
        if (!file.isFile()) return null;
        try {
            Map<String, Object> root = RegionFile.readNbt(file);
            if (root == null || !(root.get("Data") instanceof Map<?, ?> data)) return null;
            // since 1.21.9: spawn.pos, before SpawnX/Y/Z
            if (data.get("spawn") instanceof Map<?, ?> spawn && spawn.get("pos") instanceof int[] pos && pos.length == 3) {
                return new BlockVector(pos[0], pos[1], pos[2]);
            }
            if (data.get("SpawnX") instanceof Integer x && data.get("SpawnY") instanceof Integer y
                    && data.get("SpawnZ") instanceof Integer z) {
                return new BlockVector(x, y, z);
            }
        } catch (IOException | RuntimeException ex) {
            Debug.info("Couldn't read the spawn of map " + map.getName() + ": " + ex);
        }
        return null;
    }

    /**
     * the copy that is made right now (or has been made) isn't needed: it is deleted once it is done
     */
    private void discardCopy() {
        if (copy == null) return;
        this.copy = null;
        if (kept) return;
        File folder = getWorldFolder();
        FileJobs.run(() -> FileManager.deleteDirectory(folder), folder);
    }

    private static boolean copyMap(File from, File to) {
        if (!to.mkdirs() && !to.isDirectory()) return false;
        File[] files = from.listFiles();
        if (files == null) return false;
        for (File file : files) {
            if (SKIPPED_FILES.contains(file.getName())) continue;
            if (!FileManager.copyFolder(file, new File(to, file.getName()))) return false;
        }
        // holds the uuid of the world (like uid.dat before 26.1): Paper refuses to load a world with the uuid of an
        // already loaded one, e.g. if the map is a copy of a world of the server - without it Paper creates a new one
        //noinspection ResultOfMethodCallIgnored
        new File(to, METADATA_FILE).delete();
        return true;
    }

    /**
     * unloads the world - a temporary world is deleted, a kept one saved
     *
     * @return false if the world couldn't be unloaded (it stays loaded)
     */
    public boolean unload() {
        if (world == null) {
            discardCopy();
            return true;
        }
        if (!world.getPlayers().isEmpty()) {
            Debug.warn("Can't unload world '" + worldName + "', there are still players in it");
            return false;
        }
        if (!Bukkit.unloadWorld(world, kept)) {
            Debug.warn("Couldn't unload world '" + worldName + "'");
            return false;
        }
        this.world = null;
        this.copy = null;
        if (!kept) FileManager.deleteDirectory(getWorldFolder());
        return true;
    }

    /**
     * unloads and deletes the world (also a kept one: the next game starts with a fresh copy)
     */
    public void delete() {
        if (world != null) {
            Bukkit.unloadWorld(world, false);
            this.world = null;
        }
        this.copy = null;
        FileJobs.await(getWorldFolder());
        FileManager.deleteDirectory(getWorldFolder());
    }

    /**
     * unloads the world and copies it back into the folder of the map (after an admin has edited it), then deletes it
     *
     * @return false if it couldn't be saved (the copy is kept then)
     */
    public boolean closeAndSaveToMap() {
        if (world == null || map == null) return false;
        if (!world.getPlayers().isEmpty() || !Bukkit.unloadWorld(world, true)) {
            Debug.warn("Couldn't unload world '" + worldName + "' to save it");
            return false;
        }
        this.world = null;
        this.copy = null;
        File worldFolder = getWorldFolder();
        // no copy of the map is made while it is saved
        FileJobs.await(map);
        if (!copyMap(worldFolder, getMapData(map))) {
            Debug.warn("Couldn't save world '" + worldName + "' to " + map + " - the copy is kept in " + worldFolder);
            return false;
        }
        FileManager.deleteDirectory(worldFolder);
        return true;
    }

    @Nullable
    public World getWorld() {
        return world;
    }

    public String getWorldName() {
        return worldName;
    }

    /**
     * @return true if the world stays when the game is unloaded (Varo)
     */
    public boolean isKept() {
        return kept;
    }

}
