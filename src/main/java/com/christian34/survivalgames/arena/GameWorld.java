package com.christian34.survivalgames.arena;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.files.FileManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Locale;
import java.util.Set;

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
            "DIM-1", "DIM1", "dimensions", "playerdata", "players", "advancements", "stats", "datapacks");
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
    private static File getMapData(File map) {
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
     * loads the world: a kept world as it is, otherwise a fresh copy of the map (or a newly generated world)
     */
    public World load() {
        if (world != null) return world;
        File worldFolder = getWorldFolder();
        if (!exists() && !prepared) {
            FileManager.deleteDirectory(worldFolder);
            if (map != null && !copyMap(getMapData(map), worldFolder)) {
                FileManager.deleteDirectory(worldFolder);
                throw new IllegalStateException("Couldn't copy map " + map + " to " + worldFolder);
            }
        }
        WorldCreator creator = new WorldCreator(NamespacedKey.minecraft(worldName));
        if (seed != null) creator.seed(seed);
        World created = creator.createWorld();
        if (created == null) {
            if (!kept) FileManager.deleteDirectory(worldFolder);
            throw new IllegalStateException("Couldn't load world " + worldName + " (see the log above)");
        }
        created.setAutoSave(kept);
        this.world = created;
        return created;
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
        if (world == null) return true;
        if (!world.getPlayers().isEmpty()) {
            Debug.warn("Can't unload world '" + worldName + "', there are still players in it");
            return false;
        }
        if (!Bukkit.unloadWorld(world, kept)) {
            Debug.warn("Couldn't unload world '" + worldName + "'");
            return false;
        }
        this.world = null;
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
        File worldFolder = getWorldFolder();
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

}
