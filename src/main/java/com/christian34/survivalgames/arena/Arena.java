package com.christian34.survivalgames.arena;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.files.FileManager;
import com.christian34.survivalgames.files.PluginFile;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The setup of an arena in arenas.yml: the game type, the map (a folder in maps/ or a generated world), the lobby
 * (without one the players wait in the {@link Lobby} world) and spawn points (coordinates in the map), the center and size of the world border and the number of players.
 * Settings that aren't set come from the section of the game type in config.yml.
 *
 * @author Christian34
 */
public class Arena {
    public static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,32}");
    private final SurvivalGames plugin;
    private final String name;

    Arena(SurvivalGames plugin, String name) {
        this.plugin = plugin;
        this.name = name;
    }

    private PluginFile file() {
        return plugin.getFileManager().getArenasFile();
    }

    private String path(String key) {
        return name + "." + key;
    }

    private void set(String key, @Nullable Object value) {
        file().set(path(key), value);
    }

    public String getName() {
        return name;
    }

    public GameType getType() {
        GameType type = GameType.parse(file().getString(path("type")));
        return type == null ? GameType.SURVIVAL_GAMES : type;
    }

    public void setType(GameType type) {
        set("type", type.getKey());
    }

    /**
     * the settings of the game type (section of config.yml)
     */
    public ConfigurationSection settings() {
        ConfigurationSection section = plugin.getFileManager().getConfigFile().getSection(getType().getKey());
        return section != null ? section : plugin.getFileManager().getConfigFile().getData().createSection(getType().getKey());
    }

    /**
     * @return display-name (MiniMessage), otherwise the name
     */
    public Component getDisplayName() {
        String displayName = file().getString(path("display-name"));
        return displayName == null || displayName.isBlank() ? Component.text(name) : I.text(displayName);
    }

    public void setDisplayName(@Nullable String displayName) {
        set("display-name", displayName);
    }

    // ---------------------------------------------------------------- map

    /**
     * @return true if every game gets a newly generated world instead of a copy of a map
     */
    public boolean isGenerated() {
        return file().getBoolean(path("generate"));
    }

    /**
     * @return the seed of a generated world, null: random
     */
    @Nullable
    public Long getSeed() {
        return file().getData().isSet(path("seed")) ? file().getData().getLong(path("seed")) : null;
    }

    /**
     * the map in plugins/SurvivalGames/maps (several arenas may share a map)
     */
    public String getMapName() {
        String map = file().getString(path("map"));
        return map == null || map.isBlank() ? name : map;
    }

    @Nullable
    public File getMapFolder() {
        return isGenerated() ? null : new File(FileManager.getMapsFolder(), getMapName());
    }

    /**
     * @param id unique name of the world (temporary worlds: the game, kept worlds: the arena)
     */
    public GameWorld createWorld(String id, boolean kept) {
        return new GameWorld(getMapFolder(), getSeed(), id, kept);
    }

    // ---------------------------------------------------------------- players

    private int getInt(String key, int min) {
        int value = file().getData().isSet(path(key)) ? file().getInt(path(key)) : settings().getInt(key);
        return Math.max(min, value);
    }

    public int getMinPlayers() {
        return getInt("min-players", 1);
    }

    /**
     * @return max-players - with spawn points at most one player per spawn point
     */
    public int getMaxPlayers() {
        int max = Math.max(getMinPlayers(), getInt("max-players", 1));
        int spawns = getSpawnCount();
        return spawns > 0 ? Math.min(max, spawns) : max;
    }

    public void setPlayers(int min, int max) {
        set("min-players", min);
        set("max-players", max);
    }

    public int getTeamSize() {
        return getInt("team-size", 1);
    }

    public void setTeamSize(int size) {
        set("team-size", size);
    }

    /**
     * @return the size of the world border at the start (0: none)
     */
    public int getBorderSize() {
        if (file().getData().isSet(path("border"))) return Math.max(0, file().getInt(path("border")));
        return Math.max(0, settings().getInt("world-border.size"));
    }

    public void setBorderSize(int size) {
        set("border", size);
    }

    // ---------------------------------------------------------------- locations (coordinates without a world)

    static Map<String, Object> serialize(Location location) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("yaw", (double) location.getYaw());
        map.put("pitch", (double) location.getPitch());
        return map;
    }

    @Nullable
    static Location deserialize(@Nullable Object object, World world) {
        Map<?, ?> map;
        if (object instanceof ConfigurationSection section) {
            map = section.getValues(false);
        } else if (object instanceof Map<?, ?> values) {
            map = values;
        } else {
            return null;
        }
        return new Location(world, number(map.get("x")), number(map.get("y")), number(map.get("z")),
                (float) number(map.get("yaw")), (float) number(map.get("pitch")));
    }

    private static double number(@Nullable Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    /**
     * @return the lobby in the world of a game, null if it isn't set
     */
    @Nullable
    public Location getLobby(World world) {
        return deserialize(file().getData().get(path("lobby")), world);
    }

    public void setLobby(Location location) {
        set("lobby", serialize(location));
    }

    public boolean hasLobby() {
        return file().getData().isSet(path("lobby"));
    }

    /**
     * the arena has no lobby of its own anymore: the players wait in the lobby world (without one at the center)
     */
    public void removeLobby() {
        set("lobby", null);
    }

    /**
     * @return true if the players wait in the lobby world of the server instead of the map of the arena
     */
    public boolean usesLobbyWorld() {
        return !hasLobby() && plugin.getArenaManager().getLobby().isSet();
    }

    private List<Object> getRawSpawns() {
        return new ArrayList<>(file().getData().getList(path("spawns"), List.of()));
    }

    public int getSpawnCount() {
        return getRawSpawns().size();
    }

    /**
     * @return the spawn points in the world of a game (empty: the players spawn in a circle around the center)
     */
    public List<Location> getSpawns(World world) {
        List<Location> spawns = new ArrayList<>();
        for (Object spawn : getRawSpawns()) {
            Location location = deserialize(spawn, world);
            if (location != null) spawns.add(location);
        }
        return spawns;
    }

    /**
     * @return the number of the new spawn point
     */
    public int addSpawn(Location location) {
        List<Object> spawns = getRawSpawns();
        spawns.add(serialize(location));
        set("spawns", spawns);
        return spawns.size();
    }

    /**
     * @param index starts at 0
     */
    public void removeSpawn(int index) {
        List<Object> spawns = getRawSpawns();
        spawns.remove(index);
        set("spawns", spawns.isEmpty() ? null : spawns);
    }

    public void clearSpawns() {
        set("spawns", null);
    }

    /**
     * @return the center of the world border: center in arenas.yml, otherwise the middle of the spawn points, otherwise
     * the spawn of the world
     */
    public Location getCenter(World world) {
        Location center = deserialize(file().getData().get(path("center")), world);
        if (center != null) return center;
        List<Location> spawns = getSpawns(world);
        if (spawns.isEmpty()) return world.getSpawnLocation();
        double x = 0;
        double z = 0;
        for (Location spawn : spawns) {
            x += spawn.getX();
            z += spawn.getZ();
        }
        return new Location(world, x / spawns.size(), spawns.getFirst().getY(), z / spawns.size());
    }

    public void setCenter(Location location) {
        set("center", serialize(location));
    }

    // ---------------------------------------------------------------- games

    /**
     * @return true if the arena gets a game when the server starts (set by /sg arena &lt;arena&gt; creategame)
     */
    public boolean hasAutoGame() {
        return file().getBoolean(path("auto-game"));
    }

    public void setAutoGame(boolean autoGame) {
        set("auto-game", autoGame ? true : null);
    }

    /**
     * @return true if the arena has a game (its setup can't be changed then)
     */
    public boolean isInUse() {
        return plugin.getGameManager().getGame(this) != null;
    }

    /**
     * @return what has to be done before the arena can host a game (empty if nothing)
     */
    public List<Component> getMissingSetup() {
        List<Component> missing = new ArrayList<>();
        File map = getMapFolder();
        if (map != null && !map.isDirectory()) missing.add(I.i18n(LangText.SETUP_MISSING_MAP, getMapName()));
        return missing;
    }

    /**
     * @return what is set up in an unusual way (the arena can host games anyway)
     */
    public List<Component> getWarnings() {
        List<Component> warnings = new ArrayList<>();
        if (getSpawnCount() == 0) warnings.add(I.i18n(LangText.SETUP_NO_SPAWNS, name));
        if (!hasLobby() && !usesLobbyWorld()) warnings.add(I.i18n(LangText.SETUP_NO_LOBBY, name));
        int max = getInt("max-players", 1);
        if (getSpawnCount() > 0 && getSpawnCount() < max) {
            warnings.add(I.i18n(LangText.SETUP_FEW_SPAWNS, getSpawnCount(), max));
        }
        return warnings;
    }

    public boolean isPlayable() {
        return getMissingSetup().isEmpty();
    }

}
