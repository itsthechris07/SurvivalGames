package com.christian34.survivalgames.arena;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.files.FileManager;
import com.christian34.survivalgames.game.Game;
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
 * when the last admin leaves it. The lobby world (/sg lobby) is edited the same way.
 *
 * @author Christian34
 */
public class ArenaManager {
    private final SurvivalGames plugin;
    private final Map<String, Arena> arenas = new HashMap<>();
    private final Map<String, EditWorld> editWorlds = new HashMap<>();
    private final Lobby lobby = new Lobby();
    /**
     * the copy of the lobby world admins edit right now
     */
    @Nullable
    private EditWorld lobbyEdit;

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

    /**
     * the lobby world the players of all arenas without a lobby of their own wait in
     */
    public Lobby getLobby() {
        return lobby;
    }

    /**
     * @param map a map in maps/, null: no lobby world
     */
    public void setLobbyMap(@Nullable String map) {
        lobby.setMap(map);
        lobbyChanged();
    }

    /**
     * the lobby world or the arenas using it have changed: the waiting games load their copy again
     */
    public void lobbyChanged() {
        plugin.getGameManager().getGames().forEach(Game::reloadLobby);
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
     * @return the name of the map a world of the server is imported as
     */
    public static String toMapName(String worldName) {
        return worldName.replace(" ", "_").replaceAll("[^A-Za-z0-9_]", "");
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
     * @return true if the player edits the lobby world
     */
    public boolean isEditingLobby(Player player) {
        return lobbyEdit != null && lobbyEdit.editors.contains(player.getUniqueId());
    }

    /**
     * @return true if the player edits the map of an arena or the lobby world
     */
    public boolean isEditing(Player player) {
        return isEditingLobby(player) || getEditedArena(player) != null;
    }

    /**
     * @return the world of the copy of the lobby world that is edited right now, null if nobody edits it
     */
    @Nullable
    public World getLobbyEditWorld() {
        return lobbyEdit == null ? null : lobbyEdit.world.getWorld();
    }

    /**
     * @return the world of the copy the player edits (the map of an arena or the lobby world), null if he edits none
     */
    @Nullable
    public World getEditWorld(Player player) {
        if (isEditingLobby(player)) return getLobbyEditWorld();
        Arena arena = getEditedArena(player);
        return arena == null ? null : getEditWorld(arena);
    }

    /**
     * @return true if the world is a copy of a map an admin edits
     */
    public boolean isEditWorld(World world) {
        return world.equals(getLobbyEditWorld())
                || editWorlds.values().stream().anyMatch(editWorld -> world.equals(editWorld.world.getWorld()));
    }

    /**
     * the player (whose state is saved) gets into the copy in creative mode
     *
     * @return the world of the copy
     */
    private World enter(GamePlayer gamePlayer, EditWorld editWorld) {
        Player player = gamePlayer.getPlayer();
        plugin.getReplays().leave(player);
        World world = editWorld.world.load();
        editWorld.editors.add(player.getUniqueId());
        gamePlayer.getStorage().store();
        gamePlayer.getStorage().cleanPlayer();
        player.setGameMode(GameMode.CREATIVE);
        return world;
    }

    /**
     * the player leaves the copy and gets his state back
     *
     * @return true if he was the last one editing it
     */
    private boolean exit(GamePlayer gamePlayer, EditWorld editWorld) {
        Player player = gamePlayer.getPlayer();
        editWorld.editors.remove(player.getUniqueId());
        gamePlayer.getStorage().restore();
        if (editWorld.world.getWorld() != null && player.getWorld().equals(editWorld.world.getWorld())) {
            // the saved location was in the edit world (should not happen) - out of it
            player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
        }
        return editWorld.editors.isEmpty();
    }

    /**
     * the player (whose state is saved) gets into a copy of the map in creative mode
     */
    public void startEditing(GamePlayer gamePlayer, Arena arena) {
        EditWorld editWorld = editWorlds.computeIfAbsent(key(arena.getName()),
                key -> new EditWorld(arena.createWorld("edit_" + key, false)));
        World world = enter(gamePlayer, editWorld);
        Location lobby = arena.getLobby(world);
        gamePlayer.getPlayer().teleport(lobby != null ? lobby : arena.getCenter(world));
    }

    /**
     * the player (whose state is saved) gets into a copy of the lobby world in creative mode
     */
    public void startEditingLobby(GamePlayer gamePlayer) {
        if (lobbyEdit == null) this.lobbyEdit = new EditWorld(lobby.createEditWorld());
        World world = enter(gamePlayer, lobbyEdit);
        gamePlayer.getPlayer().teleport(lobby.getSpawn(world));
    }

    /**
     * the player leaves the copy of the map (or the lobby world) and gets his state back - the last one saves it
     */
    public void stopEditing(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        if (lobbyEdit != null && isEditingLobby(player)) {
            EditWorld editWorld = lobbyEdit;
            if (!exit(gamePlayer, editWorld)) return;
            this.lobbyEdit = null;
            if (editWorld.world.closeAndSaveToMap()) {
                lobbyChanged();
            } else {
                Debug.warn("The changes of the lobby world couldn't be saved");
            }
            return;
        }
        Arena arena = getEditedArena(player);
        if (arena == null) return;
        EditWorld editWorld = editWorlds.get(key(arena.getName()));
        if (!exit(gamePlayer, editWorld)) return;
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
        List<EditWorld> edited = new ArrayList<>(editWorlds.values());
        if (lobbyEdit != null) edited.add(lobbyEdit);
        for (EditWorld editWorld : edited) {
            for (UUID editor : new ArrayList<>(editWorld.editors)) {
                Player player = Bukkit.getPlayer(editor);
                if (player != null) stopEditing(plugin.getUser(player));
            }
        }
    }

}
