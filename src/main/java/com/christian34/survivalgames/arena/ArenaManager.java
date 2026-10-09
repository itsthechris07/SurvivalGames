package com.christian34.survivalgames.arena;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.files.FileManager;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.VaroGame;
import com.christian34.survivalgames.user.GamePlayer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The arenas of arenas.yml, their maps in plugins/SurvivalGames/maps and the copies admins edit
 * (/sg arena &lt;arena&gt; edit): the spawn points and the lobby are set in such a copy, which is saved back into the map
 * when the last admin leaves it.
 *
 * @author Christian34
 */
public class ArenaManager {
    private final SurvivalGames plugin;
    private final Map<String, Arena> arenas = new HashMap<>();
    private final Map<String, EditWorld> editWorlds = new HashMap<>();

    /**
     * the copy of a map admins edit right now
     */
    private static final class EditWorld {
        private final GameWorld world;
        private final Set<UUID> editors = new HashSet<>();

        private EditWorld(GameWorld world) {
            this.world = world;
        }
    }

    public ArenaManager(SurvivalGames plugin) {
        this.plugin = plugin;
        ConfigurationSection section = plugin.getFileManager().getArenasFile().getData();
        for (String name : section.getKeys(false)) {
            if (!Arena.NAME.matcher(name).matches()) {
                Debug.warn("Invalid arena name '" + name + "' in arenas.yml (letters, numbers and _ only)");
                continue;
            }
            arenas.put(key(name), new Arena(plugin, name));
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    @Nullable
    public Arena get(@Nullable String name) {
        return name == null ? null : arenas.get(key(name));
    }

    /**
     * @return all arenas, sorted by name
     */
    public List<Arena> getArenas() {
        return arenas.values().stream().sorted(Comparator.comparing(Arena::getName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    /**
     * creates the arena (the caller has checked the name and the map)
     *
     * @param generate true: every game gets a newly generated world
     * @param seed     the seed of the generated worlds (null: random)
     */
    public Arena create(String name, GameType type, boolean generate, @Nullable Long seed) {
        Arena arena = new Arena(plugin, name);
        arena.setType(type);
        if (generate) {
            plugin.getFileManager().getArenasFile().set(name + ".generate", true);
            if (seed != null) plugin.getFileManager().getArenasFile().set(name + ".seed", seed);
        }
        arenas.put(key(name), arena);
        return arena;
    }

    /**
     * removes the arena from arenas.yml and deletes a saved Varo with its world (the map stays in maps/)
     */
    public void delete(Arena arena) {
        arena.createWorld(arena.getName(), true).delete();
        File game = VaroGame.getFile(arena);
        if (game.isFile() && !game.delete()) Debug.warn("Couldn't delete " + game);
        arenas.remove(key(arena.getName()));
        plugin.getFileManager().getArenasFile().set(arena.getName(), null);
    }

    public boolean isMap(String name) {
        return new File(FileManager.getMapsFolder(), name).isDirectory();
    }

    public List<String> getMapNames() {
        File[] folders = FileManager.getMapsFolder().listFiles(File::isDirectory);
        if (folders == null) return List.of();
        return Arrays.stream(folders).map(File::getName).sorted().toList();
    }

    /**
     * worlds of the server that can be imported as a map (not the worlds of this plugin)
     */
    public static List<World> getImportableWorlds() {
        return Bukkit.getWorlds().stream().filter(world -> !GameWorld.isPluginWorld(world)).toList();
    }

    @Nullable
    public static World getImportableWorld(String name) {
        return getImportableWorlds().stream().filter(world -> world.getName().equalsIgnoreCase(name)).findFirst()
                .orElse(null);
    }

    /**
     * copies a (loaded) world of the server into plugins/SurvivalGames/maps
     *
     * @return false if the map already exists or the world couldn't be copied
     */
    public boolean importWorld(World world, String mapName) {
        File target = new File(FileManager.getMapsFolder(), mapName);
        if (target.exists()) return false;
        try {
            world.save();
        } catch (RuntimeException ex) {
            // not implemented by MockBukkit - on a server the copy would miss the latest changes
            Debug.warn("Couldn't save world '" + world.getName() + "' before copying it: " + ex);
        }
        File source = GameWorld.getFolder(world);
        if (!FileManager.copyFolder(source, target)) {
            Debug.warn("Couldn't copy world '" + world.getName() + "' from " + source + " to " + target);
            FileManager.deleteDirectory(target);
            return false;
        }
        Debug.info("Imported world '" + world.getName() + "' as map '" + mapName + "'");
        return true;
    }

    // ---------------------------------------------------------------- editing

    /**
     * @return the arena whose map the player edits, null if he doesn't edit one
     */
    @Nullable
    public Arena getEditedArena(Player player) {
        for (Map.Entry<String, EditWorld> entry : editWorlds.entrySet()) {
            if (entry.getValue().editors.contains(player.getUniqueId())) return arenas.get(entry.getKey());
        }
        return null;
    }

    /**
     * @return the world of the copy of the arena that is edited right now, null if nobody edits it
     */
    @Nullable
    public World getEditWorld(Arena arena) {
        EditWorld editWorld = editWorlds.get(key(arena.getName()));
        return editWorld == null ? null : editWorld.world.getWorld();
    }

    /**
     * @return true if the world is a copy of a map an admin edits
     */
    public boolean isEditWorld(World world) {
        return editWorlds.values().stream().anyMatch(editWorld -> world.equals(editWorld.world.getWorld()));
    }

    /**
     * the player (whose state is saved) gets into a copy of the map in creative mode
     */
    public void startEditing(GamePlayer gamePlayer, Arena arena) {
        Player player = gamePlayer.getPlayer();
        EditWorld editWorld = editWorlds.computeIfAbsent(key(arena.getName()),
                key -> new EditWorld(arena.createWorld("edit_" + key, false)));
        World world = editWorld.world.load();
        editWorld.editors.add(player.getUniqueId());
        gamePlayer.getStorage().store();
        gamePlayer.getStorage().cleanPlayer();
        player.setGameMode(GameMode.CREATIVE);
        Location lobby = arena.getLobby(world);
        player.teleport(lobby != null ? lobby : arena.getCenter(world));
    }

    /**
     * the player leaves the copy of the map and gets his state back - the last one saves the map
     */
    public void stopEditing(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        Arena arena = getEditedArena(player);
        if (arena == null) return;
        EditWorld editWorld = editWorlds.get(key(arena.getName()));
        editWorld.editors.remove(player.getUniqueId());
        gamePlayer.getStorage().restore();
        if (editWorld.world.getWorld() != null && player.getWorld().equals(editWorld.world.getWorld())) {
            // the saved location was in the edit world (should not happen) - out of it
            player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
        }
        if (!editWorld.editors.isEmpty()) return;
        editWorlds.remove(key(arena.getName()));
        if (arena.isGenerated()) {
            // nothing to save: the world is generated again for every game, the positions are in arenas.yml
            editWorld.world.unload();
        } else if (!editWorld.world.closeAndSaveToMap()) {
            Debug.warn("The changes of the map of arena '" + arena.getName() + "' couldn't be saved");
        }
    }

    /**
     * the plugin is disabled: all admins leave the copies (the maps are saved)
     */
    public void stopAll() {
        for (EditWorld editWorld : new ArrayList<>(editWorlds.values())) {
            for (UUID editor : new ArrayList<>(editWorld.editors)) {
                Player player = Bukkit.getPlayer(editor);
                if (player != null) stopEditing(plugin.getUser(player));
            }
        }
    }

}
