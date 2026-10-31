package com.christian34.survivalgames.game;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.arena.ArenaManager;
import com.christian34.survivalgames.arena.Lobby;
import com.christian34.survivalgames.lib.Menu;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * The menus of the admins (/sg admin): the arenas with the steps of their setup, the lobby world and the games with
 * their controls.
 * The buttons run the commands of the plugin, so the menus do and say the same as the commands.
 *
 * @author Christian34
 */
public final class AdminMenus {

    private static final int[] PLAYER_STEPS = {-5, -1, 1, 5};
    private static final int[] BORDER_STEPS = {-100, -10, 10, 100};

    private AdminMenus() {
    }

    private static SurvivalGames plugin() {
        return SurvivalGames.getInstance();
    }

    private static ItemStack icon(Material material) {
        return ItemStack.of(material);
    }

    /**
     * @return runs the command, then shows the menu again (with the new state)
     */
    private static Consumer<Player> run(String command, Consumer<Player> menu) {
        return player -> {
            Menus.run(player, command);
            menu.accept(player);
        };
    }

    private static void back(Menu menu, Consumer<Player> previous) {
        menu.corner(icon(Material.ARROW), i18n(LangText.GUI_BACK), List.of(), previous);
    }

    /**
     * arenas, games, settings and reload
     */
    public static void open(Player player) {
        Menu menu = new Menu(i18n(LangText.GUI_ADMIN));
        menu.button(icon(Material.GRASS_BLOCK), i18n(LangText.GUI_ADMIN_ARENAS),
                I.itemLines(LangText.GUI_ADMIN_ARENAS_LORE, plugin().getArenaManager().getArenas().size()),
                AdminMenus::openArenas);
        menu.button(icon(Material.IRON_SWORD), i18n(LangText.GUI_ADMIN_GAMES),
                I.itemLines(LangText.GUI_ADMIN_GAMES_LORE, plugin().getGameManager().getGames().size()),
                AdminMenus::openGames);
        Lobby lobby = plugin().getArenaManager().getLobby();
        menu.button(icon(Material.OAK_DOOR), i18n(LangText.GUI_ADMIN_LOBBY),
                I.itemLines(LangText.GUI_ADMIN_LOBBY_LORE, lobby.isSet() ? lobby.getMapName() : i18n(LangText.GUI_LOBBY_NONE)),
                AdminMenus::openLobby);
        menu.button(icon(Material.COMPARATOR), i18n(LangText.GUI_ADMIN_SETTINGS),
                I.itemLines(LangText.GUI_ADMIN_SETTINGS_LORE), clicker -> Menus.run(clicker, "sg settings"));
        menu.button(icon(Material.REPEATER), i18n(LangText.GUI_ADMIN_RELOAD),
                I.itemLines(LangText.GUI_ADMIN_RELOAD_LORE), clicker -> Menus.run(clicker, "sg reload"));
        menu.show(player);
    }

    // ---------------------------------------------------------------- arenas

    /**
     * all arenas with their state (click: the setup of the arena)
     */
    public static void openArenas(Player player) {
        Menu menu = new Menu(i18n(LangText.GUI_ARENAS));
        for (Arena arena : plugin().getArenaManager().getArenas()) {
            Game game = plugin().getGameManager().getGame(arena);
            Component state = game != null ? i18n(game.getState().getDisplayName())
                    : i18n(arena.isPlayable() ? LangText.ARENA_NO_GAME : LangText.ARENA_NOT_PLAYABLE);
            List<Component> lore = new ArrayList<>();
            lore.add(i18n(LangText.GUI_ARENA_TYPE, i18n(arena.getType().getDisplayName())));
            lore.add(i18n(LangText.GUI_ARENA_STATE, state));
            for (Component missing : arena.getMissingSetup()) {
                lore.add(i18n(LangText.ARENA_INFO_MISSING, missing));
            }
            lore.add(Component.empty());
            lore.add(i18n(LangText.GUI_CLICK_TO_OPEN));
            Material material = !arena.isPlayable() ? Material.BARRIER : game != null ? Material.FILLED_MAP : Material.MAP;
            menu.button(icon(material), Component.text(arena.getName(), NamedTextColor.YELLOW), lore,
                    clicker -> openArena(clicker, arena));
        }
        // the name has to be typed
        menu.button(icon(Material.WRITABLE_BOOK), i18n(LangText.GUI_ARENA_CREATE),
                I.itemLines(LangText.GUI_ARENA_CREATE_LORE),
                clicker -> clicker.sendMessage(I.prefixed(clicker, LangText.ARENA_CREATE_HINT)));
        back(menu, AdminMenus::open);
        menu.show(player);
    }

    /**
     * the steps of the setup of an arena: edit the map, set lobby, center and spawn points where the admin stands, save,
     * create the game
     */
    public static void openArena(Player player, Arena arena) {
        if (plugin().getArenaManager().get(arena.getName()) != arena) {
            // deleted in the meantime
            openArenas(player);
            return;
        }
        String command = "sg arena " + arena.getName() + " ";
        Consumer<Player> again = clicker -> openArena(clicker, arena);
        Menu menu = new Menu(i18n(LangText.GUI_ARENA, arena.getName()));
        Game game = plugin().getGameManager().getGame(arena);
        if (plugin().getArenaManager().getEditedArena(player) == arena) {
            // the admin walks to the next place, so the menu stays closed
            menu.button(icon(Material.OAK_DOOR), i18n(LangText.GUI_ARENA_SET_LOBBY),
                    I.itemLines(LangText.GUI_ARENA_HERE_LORE), clicker -> Menus.run(clicker, command + "lobby"));
            menu.button(icon(Material.BEACON), i18n(LangText.GUI_ARENA_SET_CENTER),
                    I.itemLines(LangText.GUI_ARENA_HERE_LORE), clicker -> Menus.run(clicker, command + "center"));
            menu.button(icon(Material.HEAVY_WEIGHTED_PRESSURE_PLATE), i18n(LangText.GUI_ARENA_ADD_SPAWN),
                    I.itemLines(LangText.GUI_ARENA_SPAWNS_LORE, arena.getSpawnCount()),
                    clicker -> Menus.run(clicker, command + "spawn add"));
            menu.button(icon(Material.LIME_DYE), i18n(LangText.GUI_ARENA_SAVE),
                    I.itemLines(LangText.GUI_ARENA_SAVE_LORE), run("sg arena save", again));
        } else if (game == null) {
            menu.button(icon(Material.WOODEN_AXE), i18n(LangText.GUI_ARENA_EDIT),
                    I.itemLines(LangText.GUI_ARENA_EDIT_LORE), clicker -> Menus.run(clicker, command + "edit"));
        }
        List<Component> setup = new ArrayList<>(I.itemLines(LangText.GUI_ARENA_SETUP_LORE, arena.getMinPlayers(),
                arena.getMaxPlayers(), arena.getTeamSize(), arena.getSpawnCount(), arena.getBorderSize()));
        for (Component missing : arena.getMissingSetup()) {
            setup.add(i18n(LangText.ARENA_INFO_MISSING, missing));
        }
        for (Component warning : arena.getWarnings()) {
            setup.add(i18n(LangText.ARENA_INFO_WARNING, warning));
        }
        setup.add(Component.empty());
        setup.add(i18n(LangText.GUI_ARENA_SETUP_CLICK));
        menu.button(icon(Material.BOOK), i18n(LangText.GUI_ARENA_SETUP), setup, clicker -> openSetup(clicker, arena));
        if (game == null) {
            menu.button(icon(Material.EMERALD), i18n(LangText.GUI_ARENA_CREATE_GAME),
                    I.itemLines(LangText.GUI_ARENA_CREATE_GAME_LORE), run(command + "creategame", again));
        } else {
            menu.button(icon(Material.IRON_SWORD), i18n(LangText.GUI_ARENA_GAME),
                    List.of(i18n(LangText.GUI_GAME_STATE, i18n(game.getState().getDisplayName())),
                            i18n(LangText.GUI_CLICK_TO_OPEN)),
                    clicker -> openGame(clicker, arena));
            menu.button(icon(Material.REDSTONE), i18n(LangText.GUI_ARENA_REMOVE_GAME),
                    I.itemLines(LangText.GUI_ARENA_REMOVE_GAME_LORE), run(command + "removegame", again));
        }
        back(menu, AdminMenus::openArenas);
        menu.show(player);
    }

    /**
     * the numbers of an arena (click: change one of them) and its whole setup in the chat
     */
    public static void openSetup(Player player, Arena arena) {
        String command = "sg arena " + arena.getName() + " ";
        Consumer<Player> again = clicker -> openSetup(clicker, arena);
        Menu menu = new Menu(i18n(LangText.GUI_SETUP, arena.getName()));
        number(menu, Material.PLAYER_HEAD, LangText.GUI_SETUP_MIN_PLAYERS, arena::getMinPlayers, PLAYER_STEPS, 1, 1000,
                value -> command + "players " + value + " " + Math.max(value, arena.getMaxPlayers()), again);
        number(menu, Material.PLAYER_HEAD, LangText.GUI_SETUP_MAX_PLAYERS, arena::getMaxPlayers, PLAYER_STEPS, 1, 1000,
                value -> command + "players " + Math.min(value, arena.getMinPlayers()) + " " + value, again);
        number(menu, Material.WHITE_BANNER, LangText.GUI_SETUP_TEAM_SIZE, arena::getTeamSize, PLAYER_STEPS, 1, 100,
                value -> command + "teamsize " + value, again);
        number(menu, Material.IRON_BARS, LangText.GUI_SETUP_BORDER, arena::getBorderSize, BORDER_STEPS, 0, 60000000,
                value -> command + "border " + value, again);
        menu.button(icon(Material.BOOK), i18n(LangText.GUI_SETUP_DETAILS), I.itemLines(LangText.GUI_SETUP_DETAILS_LORE),
                clicker -> Menus.run(clicker, command + "info"));
        back(menu, clicker -> openArena(clicker, arena));
        menu.show(player);
    }

    /**
     * a button with the value of a number - a click opens the menu that changes it
     */
    private static void number(Menu menu, Material material, LangText name, IntSupplier value, int[] steps, int min,
                               int max, IntFunction<String> command, Consumer<Player> previous) {
        menu.button(icon(material), i18n(name), List.of(i18n(LangText.GUI_SETTINGS_VALUE, value.getAsInt()),
                        i18n(LangText.GUI_SETTINGS_CHANGE)),
                clicker -> openNumber(clicker, name, value, steps, min, max, command, previous));
    }

    /**
     * buttons that make a number smaller or bigger (chest menus have no text fields) - every click runs the command
     * with the new value and shows the menu again
     *
     * @param steps   the changes to choose from, e.g. -5, -1, +1, +5
     * @param command the command that sets the number to the value
     */
    private static void openNumber(Player player, LangText name, IntSupplier value, int[] steps, int min, int max,
                                   IntFunction<String> command, Consumer<Player> previous) {
        int current = value.getAsInt();
        Menu menu = new Menu(i18n(LangText.GUI_NUMBER, i18n(name), current));
        for (int step : steps) {
            int changed = current + step;
            if (changed < min || changed > max) continue;
            menu.button(icon(step < 0 ? Material.RED_DYE : Material.LIME_DYE),
                    Component.text((step > 0 ? "+" : "") + step, step < 0 ? NamedTextColor.RED : NamedTextColor.GREEN),
                    List.of(i18n(LangText.GUI_SETTINGS_VALUE, changed)), run(command.apply(changed),
                            clicker -> openNumber(clicker, name, value, steps, min, max, command, previous)));
        }
        back(menu, previous);
        menu.show(player);
    }

    // ---------------------------------------------------------------- lobby world

    /**
     * the lobby world of all arenas: choose its map, edit it (set the spawn where the admin stands, save) or remove it
     */
    public static void openLobby(Player player) {
        ArenaManager arenas = plugin().getArenaManager();
        Lobby lobby = arenas.getLobby();
        Menu menu = new Menu(i18n(LangText.GUI_LOBBY));
        if (arenas.isEditingLobby(player)) {
            // the admin walks to the spawn, so the menu stays closed
            menu.button(icon(Material.HEAVY_WEIGHTED_PRESSURE_PLATE), i18n(LangText.GUI_LOBBY_SET_SPAWN),
                    I.itemLines(LangText.GUI_ARENA_HERE_LORE), clicker -> Menus.run(clicker, "sg lobby setspawn"));
            menu.button(icon(Material.LIME_DYE), i18n(LangText.GUI_LOBBY_SAVE),
                    I.itemLines(LangText.GUI_ARENA_SAVE_LORE), run("sg lobby save", AdminMenus::openLobby));
        } else {
            if (lobby.isSet()) {
                menu.button(icon(Material.WOODEN_AXE), i18n(LangText.GUI_LOBBY_EDIT),
                        I.itemLines(LangText.GUI_LOBBY_EDIT_LORE), clicker -> Menus.run(clicker, "sg lobby edit"));
            }
            menu.button(icon(Material.GRASS_BLOCK), i18n(LangText.GUI_LOBBY_CHOOSE),
                    I.itemLines(LangText.GUI_LOBBY_CHOOSE_LORE, lobby.isSet() ? lobby.getMapName() : i18n(LangText.GUI_LOBBY_NONE)),
                    AdminMenus::openLobbyWorlds);
            if (lobby.isSet()) {
                menu.button(icon(Material.BARRIER), i18n(LangText.GUI_LOBBY_REMOVE),
                        I.itemLines(LangText.GUI_LOBBY_REMOVE_LORE), run("sg lobby remove", AdminMenus::openLobby));
            }
        }
        back(menu, AdminMenus::open);
        menu.show(player);
    }

    /**
     * the maps in maps/ and the worlds of the server that can be imported (click: it becomes the lobby world)
     */
    public static void openLobbyWorlds(Player player) {
        List<String> maps = plugin().getArenaManager().getMapNames();
        Menu menu = new Menu(i18n(LangText.GUI_LOBBY_WORLDS));
        for (String map : maps) {
            menu.button(icon(Material.MAP), Component.text(map, NamedTextColor.YELLOW),
                    I.itemLines(LangText.GUI_LOBBY_MAP_LORE), run("sg lobby setworld " + map, AdminMenus::openLobby));
        }
        for (World world : ArenaManager.getImportableWorlds()) {
            String name = world.getName();
            // the name is an argument of the command
            if (maps.contains(name) || name.contains(" ")) continue;
            menu.button(icon(Material.GRASS_BLOCK), Component.text(name, NamedTextColor.YELLOW),
                    I.itemLines(LangText.GUI_LOBBY_WORLD_LORE), run("sg lobby setworld " + name, AdminMenus::openLobby));
        }
        back(menu, AdminMenus::openLobby);
        menu.show(player);
    }

    // ---------------------------------------------------------------- games

    /**
     * all games (click: the controls of the game)
     */
    public static void openGames(Player player) {
        Menu menu = new Menu(i18n(LangText.GUI_GAMES));
        for (Game game : plugin().getGameManager().getGames()) {
            Arena arena = game.getArena();
            List<Component> lore = Menus.gameLore(i18n(game.getType().getDisplayName()),
                    i18n(game.getState().getDisplayName()), game.getState() == GameState.LOBBY
                            ? game.getParticipants().size() : game.getPlayers().size(), game.getMaxPlayers());
            lore.add(i18n(LangText.GUI_CLICK_TO_OPEN));
            menu.button(icon(Menus.gameIcon(game.getState())), arena.getDisplayName(), lore,
                    clicker -> openGame(clicker, arena));
        }
        back(menu, AdminMenus::open);
        menu.show(player);
    }

    /**
     * the controls of the game of the arena, depending on its state: start, deathmatch, backups, players, stop
     */
    public static void openGame(Player player, Arena arena) {
        Game game = plugin().getGameManager().getGame(arena);
        if (game == null) {
            openGames(player);
            return;
        }
        String command = "sg game " + arena.getName() + " ";
        Consumer<Player> again = clicker -> openGame(clicker, arena);
        Menu menu = new Menu(i18n(LangText.GUI_CONTROL, arena.getName()));
        GameState state = game.getState();
        if (state == GameState.LOBBY) {
            menu.button(icon(Material.LIME_DYE), i18n(LangText.GUI_CONTROL_START),
                    I.itemLines(LangText.GUI_CONTROL_START_LORE, game.getParticipants().size(), arena.getMinPlayers()),
                    run(command + "start", again));
            menu.button(icon(Material.YELLOW_DYE), i18n(LangText.GUI_CONTROL_FORCE_START),
                    I.itemLines(LangText.GUI_CONTROL_FORCE_START_LORE), run(command + "start --force", again));
        }
        if (state == GameState.RUNNING && game instanceof SurvivalGame) {
            menu.button(icon(Material.NETHERITE_SWORD), i18n(LangText.GUI_CONTROL_DEATHMATCH),
                    I.itemLines(LangText.GUI_CONTROL_DEATHMATCH_LORE), run(command + "deathmatch", again));
        }
        if (game instanceof VaroGame && state != GameState.LOBBY) {
            menu.button(icon(Material.CHEST), i18n(LangText.GUI_CONTROL_BACKUP),
                    I.itemLines(LangText.GUI_CONTROL_BACKUP_LORE), clicker -> Menus.run(clicker, command + "backup"));
            menu.button(icon(Material.ENDER_CHEST), i18n(LangText.GUI_CONTROL_BACKUPS),
                    I.itemLines(LangText.GUI_CONTROL_BACKUPS_LORE), clicker -> Menus.run(clicker, command + "backups"));
        }
        menu.button(icon(Material.PLAYER_HEAD), i18n(LangText.GUI_CONTROL_PLAYERS),
                I.itemLines(LangText.GUI_CONTROL_PLAYERS_LORE, game.getAlive().size(), game.getParticipants().size()),
                clicker -> openPlayers(clicker, arena));
        if (arena.getTeamSize() > 1) {
            menu.button(icon(Material.WHITE_BANNER), i18n(LangText.GUI_CONTROL_TEAMS),
                    I.itemLines(LangText.GUI_CONTROL_TEAMS_LORE), clicker -> Menus.run(clicker, command + "teams"));
        }
        if (state != GameState.LOBBY && plugin().getUser(player).getGame() == null) {
            menu.button(icon(Material.ENDER_EYE), i18n(LangText.GUI_CONTROL_WATCH),
                    I.itemLines(LangText.GUI_CONTROL_WATCH_LORE),
                    clicker -> Menus.run(clicker, "sg spectate " + arena.getName()));
        }
        menu.button(icon(Material.BARRIER), i18n(LangText.GUI_CONTROL_STOP),
                I.itemLines(game instanceof VaroGame ? LangText.GUI_CONTROL_STOP_VARO_LORE : LangText.GUI_CONTROL_STOP_LORE),
                run(command + "stop", again));
        back(menu, AdminMenus::openGames);
        menu.show(player);
    }

    /**
     * everybody who takes part in the game (click: revive a player who is out, give a strike in a Varo)
     */
    public static void openPlayers(Player player, Arena arena) {
        Game game = plugin().getGameManager().getGame(arena);
        if (game == null) {
            openGames(player);
            return;
        }
        Consumer<Player> again = clicker -> openPlayers(clicker, arena);
        boolean running = game.getState() == GameState.RUNNING;
        Menu menu = new Menu(i18n(LangText.GUI_CONTROL_PLAYERS_TITLE, arena.getName()));
        // the players alive first, then by name
        List<Participant> participants = game.getParticipants().stream()
                .sorted(Comparator.comparing((Participant participant) -> !participant.isAlive())
                        .thenComparing(Participant::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        for (Participant participant : participants) {
            String name = participant.getName();
            List<Component> lore = new ArrayList<>();
            lore.add(i18n(participant.isAlive() ? LangText.GUI_CONTROL_ALIVE : LangText.GUI_CONTROL_OUT));
            GameTeam team = game.getTeam(participant.getUuid());
            if (team != null && arena.getTeamSize() > 1) lore.add(i18n(LangText.SCOREBOARD_TEAM, team.getDisplayName()));
            lore.add(i18n(LangText.SCOREBOARD_KILLS, participant.getKills()));
            Consumer<Player> action = clicker -> {
            };
            if (!participant.isAlive() && running) {
                lore.add(i18n(LangText.GUI_CONTROL_REVIVE));
                action = run("sg game " + arena.getName() + " revive " + name, again);
            } else if (game instanceof VaroGame && running) {
                lore.add(i18n(LangText.SCOREBOARD_STRIKES, participant.getStrikes().size()));
                lore.add(i18n(LangText.GUI_CONTROL_STRIKE));
                // the reason has to be typed
                action = clicker -> clicker.sendMessage(I.prefixed(clicker, LangText.STRIKE_HINT, arena.getName(), name));
            }
            menu.button(Menus.head(participant.getPlayer()), Component.text(name,
                    participant.isAlive() ? NamedTextColor.GREEN : NamedTextColor.RED), lore, action);
        }
        back(menu, clicker -> openGame(clicker, arena));
        menu.show(player);
    }

}
