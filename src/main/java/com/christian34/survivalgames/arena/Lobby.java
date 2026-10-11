package com.christian34.survivalgames.arena;

import com.christian34.survivalgames.files.FileManager;
import com.christian34.survivalgames.files.PluginFile;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * The lobby world (lobby.yml): a map in maps/ the players of all arenas wait in before their game starts, unless
 * their arena has a lobby of its own in its map. Every game gets its own copy of it, so the players of different
 * games don't meet.
 *
 * @author Christian34
 */
public class Lobby {
    private final PluginFile file = new FileManager.DataFile(new File(FileManager.getPluginFolder(), "lobby.yml"));

    /**
     * @return the map in maps/, null: there is no lobby world
     */
    @Nullable
    public String getMapName() {
        String map = file.getString("map");
        return map == null || map.isBlank() ? null : map;
    }

    @Nullable
    private File getMapFolder() {
        String map = getMapName();
        return map == null ? null : new File(FileManager.getMapsFolder(), map);
    }

    /**
     * @return true if there is a lobby world the players can wait in
     */
    public boolean isSet() {
        File map = getMapFolder();
        return map != null && map.isDirectory();
    }

    /**
     * @param map a map in maps/, null: no lobby world (the spawn belongs to the old map and is forgotten)
     */
    void setMap(@Nullable String map) {
        file.set("map", map);
        file.set("spawn", null);
    }

    public void setSpawn(Location location) {
        file.set("spawn", Arena.serialize(location));
    }

    /**
     * @return the spawn in a copy of the lobby world, without one the spawn of the world
     */
    public Location getSpawn(World world) {
        Location spawn = Arena.deserialize(file.getData().get("spawn"), world);
        return spawn != null ? spawn : world.getSpawnLocation();
    }

    /**
     * @param id unique name of the copy (the game)
     * @return a copy of the lobby world (check {@link #isSet()} first), not loaded yet
     */
    public GameWorld createWorld(String id) {
        return new GameWorld(getMapFolder(), null, "lobby_" + id, false);
    }

    /**
     * @return the copy of the map an admin edits
     */
    GameWorld createEditWorld() {
        return new GameWorld(getMapFolder(), null, "lobbyedit", false);
    }

    /**
     * nothing happens in the lobby of a game: no mobs, the time and the weather stay as they are
     */
    public static void setup(World world) {
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
    }

}
