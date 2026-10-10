package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.arena.ArenaManager;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotation.specifier.Range;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Flag;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.christian34.survivalgames.commands.CommandSurvivalGames.ADMIN;
import static com.christian34.survivalgames.commands.CommandSurvivalGames.ROOT;
import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * /sg arena: admins create arenas from a map (a folder in maps/, an imported world or a generated world), set the
 * lobby and the spawn points in a copy of the map, the number of players, the world border and the game type.
 *
 * @author Christian34
 */
@Permission(ADMIN)
public class CommandArena {
    static final String ARENA = ROOT + " arena";
    static final String ONE_ARENA = ARENA + " <arena>";

    private static SurvivalGames plugin() {
        return SurvivalGames.getInstance();
    }

    private static ArenaManager arenas() {
        return plugin().getArenaManager();
    }

    private static void send(CommandSender sender, LangText message, Object... arguments) {
        sender.sendMessage(I.prefixed(message, arguments));
    }

    @Suggestions("importable-worlds")
    public List<String> importableWorlds(CommandContext<CommandSender> context, String input) {
        return ArenaManager.getImportableWorlds().stream().map(World::getName).toList();
    }

    @Suggestions("maps")
    public List<String> maps(CommandContext<CommandSender> context, String input) {
        return arenas().getMapNames();
    }

    @Suggestions("maps-and-worlds")
    public List<String> mapsAndWorlds(CommandContext<CommandSender> context, String input) {
        Set<String> names = new LinkedHashSet<>(arenas().getMapNames());
        names.addAll(importableWorlds(context, input));
        return List.copyOf(names);
    }

    @Command(ARENA + " create <name>")
    @CommandDescription("command_arena_create")
    public void createCmd(CommandSender sender, @Argument(value = "name", suggestions = "maps-and-worlds") String name,
                          @Flag(value = "type", suggestions = "game-types") @Nullable String typeName,
                          @Flag(value = "map", suggestions = "maps") @Nullable String mapName,
                          @Flag(value = "world", suggestions = "importable-worlds") @Nullable String worldName,
                          @Flag("generate") boolean generate,
                          @Flag("seed") @Nullable Long seed) {
        if (!Arena.NAME.matcher(name).matches()) {
            send(sender, LangText.ARENA_INVALID_NAME);
            return;
        }
        if (arenas().get(name) != null) {
            send(sender, LangText.ARENA_EXISTS, name);
            return;
        }
        GameType type = typeName == null ? GameType.SURVIVAL_GAMES : GameType.parse(typeName);
        if (type == null) {
            send(sender, LangText.UNKNOWN_GAME_TYPE, typeName, String.join(", ", GameType.keys()));
            return;
        }
        String map = mapName != null ? mapName : name;
        if (!generate) {
            if (worldName == null && !arenas().isMap(map)) {
                // the name of a world of the server: imported
                World world = ArenaManager.getImportableWorld(map);
                if (world != null) worldName = world.getName();
            }
            if (worldName != null) {
                World world = ArenaManager.getImportableWorld(worldName);
                if (world == null) {
                    send(sender, LangText.WORLD_NOT_FOUND, worldName);
                    return;
                }
                if (arenas().isMap(map)) {
                    send(sender, LangText.MAP_EXISTS, map);
                    return;
                }
                if (!arenas().importWorld(world, map)) {
                    send(sender, LangText.WORLD_COPY_FAILED, world.getName());
                    return;
                }
            } else if (!arenas().isMap(map)) {
                send(sender, LangText.MAP_MISSING, map);
                return;
            }
        }
        Arena arena = arenas().create(name, type, generate, seed);
        if (!generate && !map.equals(name)) plugin().getFileManager().getArenasFile().set(name + ".map", map);
        send(sender, LangText.ARENA_CREATED, arena.getName(), i18n(type.getDisplayName()));
    }

    @Command(ARENA + " list")
    @CommandDescription("command_arena_list")
    public void listCmd(CommandSender sender) {
        List<Arena> all = arenas().getArenas();
        if (all.isEmpty()) {
            send(sender, LangText.NO_ARENAS);
            return;
        }
        sender.sendMessage(i18n(LangText.ARENA_LIST_TITLE, all.size()));
        for (Arena arena : all) {
            Game game = plugin().getGameManager().getGame(arena);
            Component state = game != null ? i18n(game.getState().getDisplayName())
                    : i18n(arena.isPlayable() ? LangText.ARENA_NO_GAME : LangText.ARENA_NOT_PLAYABLE);
            sender.sendMessage(i18n(LangText.ARENA_LIST_ENTRY, arena.getName(), i18n(arena.getType().getDisplayName()),
                    state));
        }
    }

    @Command(ONE_ARENA + " info")
    @CommandDescription("command_arena_info")
    public void infoCmd(CommandSender sender, @Argument("arena") Arena arena) {
        sender.sendMessage(i18n(LangText.ARENA_INFO_TITLE, arena.getDisplayName()));
        sender.sendMessage(i18n(LangText.ARENA_INFO_TYPE, i18n(arena.getType().getDisplayName()), arena.getName()));
        sender.sendMessage(arena.isGenerated()
                ? i18n(LangText.ARENA_INFO_GENERATED, arena.getSeed() == null ? "-" : arena.getSeed())
                : i18n(LangText.ARENA_INFO_MAP, arena.getMapName()));
        sender.sendMessage(i18n(LangText.ARENA_INFO_PLAYERS, arena.getMinPlayers(), arena.getMaxPlayers(),
                arena.getTeamSize(), arena.getName()));
        sender.sendMessage(i18n(LangText.ARENA_INFO_SPAWNS, arena.getSpawnCount(), arena.getName()));
        sender.sendMessage(i18n(LangText.ARENA_INFO_BORDER, arena.getBorderSize(), arena.getName()));
        for (Component missing : arena.getMissingSetup()) {
            sender.sendMessage(i18n(LangText.ARENA_INFO_MISSING, missing));
        }
        for (Component warning : arena.getWarnings()) {
            sender.sendMessage(i18n(LangText.ARENA_INFO_WARNING, warning));
        }
        Game game = plugin().getGameManager().getGame(arena);
        sender.sendMessage(game != null ? i18n(LangText.ARENA_INFO_GAME, i18n(game.getState().getDisplayName()))
                : i18n(LangText.ARENA_INFO_NO_GAME, arena.getName()));
    }

    // ---------------------------------------------------------------- editing the map

    @Command(ONE_ARENA + " edit")
    @CommandDescription("command_arena_edit")
    public void editCmd(Player player, @Argument("arena") Arena arena) {
        if (plugin().getUser(player).getGame() != null) {
            send(player, LangText.ALREADY_IN_GAME, plugin().getUser(player).getGame().getArena().getName());
            return;
        }
        Arena edited = arenas().getEditedArena(player);
        if (edited != null) {
            send(player, LangText.ALREADY_EDITING, edited.getName());
            return;
        }
        if (!arena.isPlayable()) {
            arena.getMissingSetup().forEach(missing -> player.sendMessage(I.prefixed(missing)));
            return;
        }
        try {
            arenas().startEditing(plugin().getUser(player), arena);
        } catch (RuntimeException ex) {
            send(player, LangText.WORLD_LOAD_FAILED, arena.getName());
            return;
        }
        send(player, LangText.EDIT_STARTED, arena.getName());
    }

    @Command(ARENA + " save")
    @CommandDescription("command_arena_save")
    public void saveCmd(Player player) {
        Arena arena = arenas().getEditedArena(player);
        if (arena == null) {
            send(player, LangText.NOT_EDITING);
            return;
        }
        arenas().stopEditing(plugin().getUser(player));
        send(player, LangText.EDIT_SAVED, arena.getName());
    }

    /**
     * @return the location of the player in the copy of the map, null if he isn't there (he got a message)
     */
    @Nullable
    private static Location position(Player player, Arena arena) {
        World world = arenas().getEditWorld(arena);
        if (world == null || !player.getWorld().equals(world)) {
            send(player, LangText.NOT_IN_EDIT_WORLD, arena.getName());
            return null;
        }
        return player.getLocation();
    }

    private static String format(Location location) {
        return location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    @Command(ONE_ARENA + " lobby")
    @CommandDescription("command_arena_lobby")
    public void lobbyCmd(Player player, @Argument("arena") Arena arena) {
        Location location = position(player, arena);
        if (location == null) return;
        arena.setLobby(location);
        send(player, LangText.LOBBY_SET, format(location));
    }

    @Command(ONE_ARENA + " center")
    @CommandDescription("command_arena_center")
    public void centerCmd(Player player, @Argument("arena") Arena arena) {
        Location location = position(player, arena);
        if (location == null) return;
        arena.setCenter(location);
        send(player, LangText.CENTER_SET, format(location));
    }

    @Command(ONE_ARENA + " spawn add")
    @CommandDescription("command_arena_spawn_add")
    public void spawnAddCmd(Player player, @Argument("arena") Arena arena) {
        Location location = position(player, arena);
        if (location == null) return;
        // the middle of the block at the height of the feet (on a slab half a block above the block); in the game the
        // players look at the center
        Location spawn = new Location(location.getWorld(), location.getBlockX() + 0.5, location.getY(),
                location.getBlockZ() + 0.5, Math.round(location.getYaw() / 45f) * 45f, 0);
        int number = arena.addSpawn(spawn);
        send(player, LangText.SPAWN_ADDED, number, arena.getName());
    }

    @Command(ONE_ARENA + " spawn list")
    @CommandDescription("command_arena_spawn_list")
    public void spawnListCmd(CommandSender sender, @Argument("arena") Arena arena) {
        World world = arenas().getEditWorld(arena);
        if (world == null && sender instanceof Player player) world = player.getWorld();
        if (world == null) world = org.bukkit.Bukkit.getWorlds().getFirst();
        List<Location> spawns = arena.getSpawns(world);
        if (spawns.isEmpty()) {
            send(sender, LangText.NO_SPAWNS, arena.getName());
            return;
        }
        sender.sendMessage(i18n(LangText.SPAWN_LIST_TITLE, spawns.size(), arena.getName()));
        for (int i = 0; i < spawns.size(); i++) {
            sender.sendMessage(i18n(LangText.SPAWN_LIST_ENTRY, i + 1, format(spawns.get(i)), arena.getName()));
        }
    }

    /**
     * @return false if there is no such spawn point (the sender got a message)
     */
    private static boolean checkSpawn(CommandSender sender, Arena arena, int number) {
        if (number >= 1 && number <= arena.getSpawnCount()) return true;
        send(sender, LangText.SPAWN_NOT_FOUND, number, arena.getName());
        return false;
    }

    @Command(ONE_ARENA + " spawn remove <number>")
    @CommandDescription("command_arena_spawn_remove")
    public void spawnRemoveCmd(CommandSender sender, @Argument("arena") Arena arena, @Argument("number") int number) {
        if (!checkSpawn(sender, arena, number)) return;
        arena.removeSpawn(number - 1);
        send(sender, LangText.SPAWN_REMOVED, number);
    }

    @Command(ONE_ARENA + " spawn tp <number>")
    @CommandDescription("command_arena_spawn_tp")
    public void spawnTpCmd(Player player, @Argument("arena") Arena arena, @Argument("number") int number) {
        if (!checkSpawn(player, arena, number)) return;
        World world = arenas().getEditWorld(arena);
        if (world == null || !player.getWorld().equals(world)) {
            send(player, LangText.NOT_IN_EDIT_WORLD, arena.getName());
            return;
        }
        player.teleport(arena.getSpawns(world).get(number - 1));
    }

    @Command(ONE_ARENA + " spawn clear")
    @CommandDescription("command_arena_spawn_clear")
    public void spawnClearCmd(CommandSender sender, @Argument("arena") Arena arena, @Flag("confirm") boolean confirm) {
        if (!confirm) {
            send(sender, LangText.SPAWNS_CLEAR_CONFIRM, arena.getName());
            return;
        }
        arena.clearSpawns();
        send(sender, LangText.SPAWNS_CLEARED);
    }

    // ---------------------------------------------------------------- settings

    @Command(ONE_ARENA + " players <min> <max>")
    @CommandDescription("command_arena_players")
    public void playersCmd(CommandSender sender, @Argument("arena") Arena arena,
                           @Argument("min") @Range(min = "1", max = "1000") int min,
                           @Argument("max") @Range(min = "1", max = "1000") int max) {
        if (max < min) {
            send(sender, LangText.PLAYERS_INVALID);
            return;
        }
        arena.setPlayers(min, max);
        send(sender, LangText.PLAYERS_SET, min, max);
        if (arena.getSpawnCount() > 0 && arena.getSpawnCount() < max) {
            send(sender, LangText.SETUP_FEW_SPAWNS, arena.getSpawnCount(), max);
        }
    }

    @Command(ONE_ARENA + " teamsize <size>")
    @CommandDescription("command_arena_teamsize")
    public void teamSizeCmd(CommandSender sender, @Argument("arena") Arena arena,
                            @Argument("size") @Range(min = "1", max = "100") int size) {
        arena.setTeamSize(size);
        send(sender, LangText.TEAM_SIZE_SET, size);
    }

    @Command(ONE_ARENA + " border <size>")
    @CommandDescription("command_arena_border")
    public void borderCmd(CommandSender sender, @Argument("arena") Arena arena,
                          @Argument("size") @Range(min = "0", max = "60000000") int size) {
        arena.setBorderSize(size);
        send(sender, size == 0 ? LangText.BORDER_REMOVED : LangText.BORDER_SET, size);
    }

    @Command(ONE_ARENA + " type <type>")
    @CommandDescription("command_arena_type")
    public void typeCmd(CommandSender sender, @Argument("arena") Arena arena,
                        @Argument(value = "type", suggestions = "game-types") String typeName) {
        GameType type = GameType.parse(typeName);
        if (type == null) {
            send(sender, LangText.UNKNOWN_GAME_TYPE, typeName, String.join(", ", GameType.keys()));
            return;
        }
        if (arena.isInUse()) {
            send(sender, LangText.ARENA_IN_USE, arena.getName());
            return;
        }
        arena.setType(type);
        send(sender, LangText.TYPE_SET, i18n(type.getDisplayName()));
    }

    @Command(ONE_ARENA + " displayname <name>")
    @CommandDescription("command_arena_displayname")
    public void displayNameCmd(CommandSender sender, @Argument("arena") Arena arena,
                               @Argument("name") @Greedy String name) {
        arena.setDisplayName(name.equalsIgnoreCase("reset") ? null : name);
        send(sender, LangText.DISPLAY_NAME_SET, arena.getDisplayName());
    }

    // ---------------------------------------------------------------- games

    /**
     * creates the game of the arena (if it has none)
     */
    static void createGame(CommandSender sender, Arena arena) {
        if (plugin().getNetworkManager().isLobby()) {
            send(sender, LangText.NO_GAMES_ON_LOBBY);
            return;
        }
        if (plugin().getGameManager().getGame(arena) != null) {
            send(sender, LangText.GAME_EXISTS, arena.getName());
            return;
        }
        if (!arena.isPlayable()) {
            arena.getMissingSetup().forEach(missing -> sender.sendMessage(I.prefixed(missing)));
            return;
        }
        try {
            plugin().getGameManager().createGame(arena);
        } catch (RuntimeException ex) {
            send(sender, LangText.WORLD_LOAD_FAILED, arena.getName());
            return;
        }
        send(sender, LangText.GAME_CREATED, arena.getName());
    }

    @Command(ONE_ARENA + " creategame")
    @CommandDescription("command_arena_creategame")
    public void createGameCmd(CommandSender sender, @Argument("arena") Arena arena) {
        arena.setAutoGame(true);
        createGame(sender, arena);
    }

    @Command(ONE_ARENA + " removegame")
    @CommandDescription("command_arena_removegame")
    public void removeGameCmd(CommandSender sender, @Argument("arena") Arena arena) {
        arena.setAutoGame(false);
        Game game = plugin().getGameManager().getGame(arena);
        if (game != null) plugin().getGameManager().stopGame(game, false);
        send(sender, LangText.GAME_REMOVED, arena.getName());
    }

    @Command(ONE_ARENA + " delete")
    @CommandDescription("command_arena_delete")
    public void deleteCmd(CommandSender sender, @Argument("arena") Arena arena, @Flag("confirm") boolean confirm) {
        if (arena.isInUse() || arenas().getEditWorld(arena) != null) {
            send(sender, LangText.ARENA_IN_USE, arena.getName());
            return;
        }
        if (!confirm) {
            send(sender, LangText.ARENA_DELETE_CONFIRM, arena.getName());
            return;
        }
        arenas().delete(arena);
        send(sender, LangText.ARENA_DELETED, arena.getName());
    }

}
