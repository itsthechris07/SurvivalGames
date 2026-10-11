package com.christian34.survivalgames.game;

import com.christian34.survivalgames.FakeForms;
import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.arena.Arena;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The main menu (/sg), the menu of the games (/sg games) and the menus of the admins (/sg admin): the buttons do the
 * same as the commands.
 *
 * @author Christian34
 */
class MenusTest extends PluginTestBase {
    private final FakeForms forms = new FakeForms();
    private PlayerMock admin;
    private PlayerMock steve;

    @BeforeEach
    void setUpPlayers() {
        plugin.getStats().setExecutor(Runnable::run);
        config().set("survival-games.lobby-countdown", 1);
        config().set("survival-games.start-countdown", 1);
        config().set("survival-games.grace-period", 0);
        config().set("varo.start-countdown", 1);
        config().set("survival-games.map-voting.enabled", false);
        plugin.setBedrockForms(forms);
        admin = addAdmin("Admin");
        steve = addPlayer("Steve");
    }

    private void click(PlayerMock player, String button) {
        forms.click(player, button);
        server.getScheduler().performOneTick();
    }

    private void run(Game game) {
        for (int tick = 0; tick < 20 * 10 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());
    }

    // ---------------------------------------------------------------- main menu

    @Test
    void theMainMenuOfPlayers() {
        execute(steve, "sg");
        assertEquals("SurvivalGames", FakeForms.plain(forms.last().getTitle()));
        assertEquals(List.of("Play", "Statistics", "Top players", "Achievements", "Replays", "Help"), forms.buttons());
        assertTrue(forms.lore("Help").contains("Version " + plugin.getPluginMeta().getVersion()));

        click(steve, "Statistics");
        assertContains(messages(steve), "There are no statistics of Steve yet.");
        click(steve, "Help");
        assertFalse(messages(steve).isEmpty());
    }

    @Test
    void helpFindsCommandsWithoutTheirRoot() {
        execute(admin, "sg help arena");
        List<String> help = messages(admin);
        assertContains(help, "/sg arena");
        assertNotContains(help, "No results");
        execute(admin, "sg help sg arena");
        assertContains(messages(admin), "/sg arena");
        execute(admin, "sg help varo team");
        assertNotContains(messages(admin), "No results");
    }

    @Test
    void theConsoleGetsTheVersion() {
        execute(server.getConsoleSender(), "sg");
        assertContains(messages(server.getConsoleSender()), "SurvivalGames v" + plugin.getPluginMeta().getVersion());
        assertTrue(forms.menus.isEmpty());
    }

    @Test
    void theMainMenuHidesWhatIsTurnedOff() {
        config().set("stats.enabled", false);
        config().set("replays.enabled", false);
        plugin.reload();
        execute(steve, "sg");
        assertEquals(List.of("Play", "Help"), forms.buttons());
    }

    @Test
    void adminsGetTheAdministration() {
        execute(admin, "sg");
        assertEquals("Administration", forms.buttons().getLast());
        click(admin, "Administration");
        assertEquals("Administration", FakeForms.plain(forms.last().getTitle()));
        assertEquals(List.of("Arenas", "Games", "Settings", "Reload"), forms.buttons());

        execute(steve, "sg admin");
        assertContains(messages(steve), "permission");
    }

    @Test
    void playersInAGameCanLeaveInTheMainMenu() {
        createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        execute(steve, "sg join arena1");
        execute(steve, "sg");
        assertEquals("Leave", forms.buttons().getFirst());
        assertFalse(forms.buttons().contains("Play"));
        click(steve, "Leave");
        assertNull(plugin.getUser(steve).getGame());
    }

    // ---------------------------------------------------------------- games

    @Test
    void theMenuOfTheGamesJoinsAndWatches() {
        Game waiting = createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        Game running = createGame("arena2", GameType.SURVIVAL_GAMES, 4);
        execute(addPlayer("Alex"), "sg join arena2");
        execute(addPlayer("Bob"), "sg join arena2");
        run(running);

        execute(steve, "sg");
        click(steve, "Play");
        assertEquals("Games", FakeForms.plain(forms.last().getTitle()));
        assertEquals(List.of("arena1", "arena2"), forms.buttons());
        assertEquals(List.of("Survival Games", "State: Lobby", "Players: 0/4", "", "Click to join"),
                forms.lore("arena1"));
        assertTrue(forms.lore("arena2").contains("Click to watch"));

        click(steve, "arena1");
        assertSame(waiting, plugin.getUser(steve).getGame());
        assertFalse(plugin.getUser(steve).isSpectator());

        execute(admin, "sg games");
        click(admin, "arena2");
        assertSame(running, plugin.getUser(admin).getGame());
        assertTrue(plugin.getUser(admin).isSpectator());
    }

    @Test
    void noGames() {
        execute(steve, "sg games");
        assertContains(messages(steve), "There are no games right now.");
        assertTrue(forms.menus.isEmpty());
    }

    // ---------------------------------------------------------------- arenas

    @Test
    void anArenaIsSetUpWithTheMenu() {
        createMap("castle");
        Arena arena = plugin.getArenaManager().create("castle", GameType.SURVIVAL_GAMES, false, null);
        execute(admin, "sg admin");
        click(admin, "Arenas");
        assertEquals(List.of("castle", "Create an arena", "Back"), forms.buttons());
        assertTrue(forms.lore("castle").contains("Game: no game"));

        click(admin, "Create an arena");
        assertContains(messages(admin), "Create an arena: /sg arena create <name>");

        click(admin, "castle");
        assertEquals("Arena castle", FakeForms.plain(forms.last().getTitle()));
        assertEquals(List.of("Edit the map", "Setup", "Create the game", "Back"), forms.buttons());
        assertTrue(forms.lore("Setup").contains("Spawn points: 0"));

        click(admin, "Edit the map");
        assertSame(arena, plugin.getArenaManager().getEditedArena(admin));
        messages(admin);

        execute(admin, "sg admin");
        click(admin, "Arenas");
        click(admin, "castle");
        assertEquals(List.of("Set the lobby", "Set the center", "Add a spawn point", "Save the map", "Setup",
                "Create the game", "Back"), forms.buttons());
        admin.teleport(new Location(admin.getWorld(), 10.5, 70, 10.5));
        click(admin, "Add a spawn point");
        assertEquals(1, arena.getSpawnCount());
        click(admin, "Set the center");
        click(admin, "Set the lobby");
        assertTrue(arena.hasLobby());

        click(admin, "Save the map");
        assertNull(plugin.getArenaManager().getEditedArena(admin));
        assertEquals("Edit the map", forms.buttons().getFirst(), "the menu is shown again");

        click(admin, "Create the game");
        assertNotNull(plugin.getGameManager().getGame(arena));
        assertEquals(List.of("Setup", "Control the game", "Remove the game", "Back"), forms.buttons());

        click(admin, "Remove the game");
        assertNull(plugin.getGameManager().getGame(arena));
        assertTrue(forms.buttons().contains("Create the game"));

        click(admin, "Back");
        assertEquals("Arenas", FakeForms.plain(forms.last().getTitle()));
        click(admin, "Back");
        assertEquals("Administration", FakeForms.plain(forms.last().getTitle()));
    }

    // ---------------------------------------------------------------- controlling games

    @Test
    void aGameIsControlledWithTheMenu() {
        Game game = createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        PlayerMock alex = addPlayer("Alex");
        execute(steve, "sg join arena1");
        execute(admin, "sg admin");
        click(admin, "Games");
        assertEquals(List.of("arena1", "Back"), forms.buttons());
        click(admin, "arena1");
        assertEquals("Game arena1", FakeForms.plain(forms.last().getTitle()));
        assertEquals(List.of("Start", "Start anyway", "Players", "Stop", "Back"), forms.buttons());
        assertEquals(List.of("Players: 1 (needed: 2)"), forms.lore("Start"));

        click(admin, "Start");
        assertContains(messages(admin), "The game can't start");
        assertEquals(GameState.LOBBY, game.getState());

        execute(alex, "sg join arena1");
        click(admin, "Start anyway");
        run(game);

        execute(admin, "sg admin");
        click(admin, "Games");
        click(admin, "arena1");
        assertEquals(List.of("Deathmatch", "Players", "Watch", "Stop", "Back"), forms.buttons());
    }

    @Test
    void playersOfAGameAreRevivedWithTheMenu() {
        config().set("survival-games.deathmatch.players", 0);
        Game game = createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        PlayerMock alex = addPlayer("Alex");
        PlayerMock bob = addPlayer("Bob");
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        execute(bob, "sg join arena1");
        run(game);
        bob.setHealth(0);
        bob.respawn();
        server.getScheduler().performOneTick();
        assertFalse(game.getParticipant(bob.getUniqueId()).isAlive());

        execute(admin, "sg admin");
        click(admin, "Games");
        click(admin, "arena1");
        assertEquals(List.of("Alive: 2/3"), forms.lore("Players"));
        click(admin, "Players");
        assertEquals("Players in arena1", FakeForms.plain(forms.last().getTitle()));
        assertEquals(List.of("Alex", "Steve", "Bob", "Back"), forms.buttons(), "alive first, then by name");
        assertEquals(List.of("alive", "Kills: 0"), forms.lore("Steve"));
        assertEquals(List.of("out", "Kills: 0", "Click to revive"), forms.lore("Bob"));

        click(admin, "Bob");
        assertTrue(game.getParticipant(bob.getUniqueId()).isAlive());
        assertEquals(List.of("alive", "Kills: 0"), forms.lore("Bob"), "the menu is shown again");

        click(admin, "Back");
        click(admin, "Stop");
        assertContains(messages(admin), "has been stopped");
    }

    @Test
    void aVaroIsControlledWithTheMenu() {
        Game game = createGame("varo1", GameType.VARO, 4);
        game.getArena().setTeamSize(1);
        execute(steve, "sg join varo1");
        execute(addPlayer("Alex"), "sg join varo1");
        execute(admin, "sg game varo1 start");
        run(game);

        execute(admin, "sg admin");
        click(admin, "Games");
        click(admin, "varo1");
        assertEquals(List.of("Make a backup", "Backups", "Players", "Watch", "Stop", "Back"), forms.buttons());
        click(admin, "Players");
        assertEquals(List.of("alive", "Kills: 0", "Strikes: 0", "Click to give a strike"), forms.lore("Steve"));
        click(admin, "Steve");
        assertContains(messages(admin), "/sg game varo1 strike Steve [reason]");
    }

}
