package com.christian34.survivalgames.network;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * One game per server in a network: the game servers and the lobby server.
 *
 * @author Christian34
 */
class NetworkTest extends PluginTestBase {

    private NetworkManager network() {
        return plugin.getNetworkManager();
    }

    /**
     * this server runs the game of arena1
     */
    private Game gameServer() {
        Arena arena = createArena("arena1", GameType.SURVIVAL_GAMES, 2);
        config().set("network.mode", "game");
        config().set("network.arena", "arena1");
        config().set("survival-games.lobby-countdown", 300);
        network().ensureGame();
        Game game = plugin.getGameManager().getGame(arena);
        assertNotNull(game);
        return game;
    }

    /**
     * this server is the lobby of the game servers sg-1 and sg-2
     */
    private void lobbyServer() {
        config().set("network.mode", "lobby");
        config().set("network.servers", List.of(Map.of("name", "sg-1", "address", "localhost:25566"),
                Map.of("name", "sg-2", "address", "localhost:25567")));
        // no pings of the servers, the tests set their status
        server.getScheduler().cancelTasks(plugin);
    }

    @Test
    void statusIsTheMotd() {
        ServerStatus status = new ServerStatus(ServerStatus.State.LOBBY, 3, 12, "arena1", "survival-games");
        assertEquals("SG;LOBBY;3;12;arena1;survival-games", status.toMotd());
        assertEquals(status, ServerStatus.parse(status.toMotd()));
        assertTrue(status.isJoinable());
        var closed = new ServerStatus(ServerStatus.State.INGAME, 2, 12, "varo1", "varo", Instant.ofEpochSecond(1793210400));
        assertEquals("SG;INGAME;2;12;varo1;varo;1793210400", closed.toMotd(), "a Varo outside of its play hours");
        assertEquals(closed, ServerStatus.parse(closed.toMotd()));
        assertEquals(ServerStatus.OFFLINE, ServerStatus.parse("A Minecraft Server"));
        assertEquals(ServerStatus.OFFLINE, ServerStatus.parse(null));
    }

    @Test
    void gameServerShowsItsGame() {
        Game game = gameServer();
        assertEquals(new ServerStatus(ServerStatus.State.LOBBY, 0, 2, "arena1", "survival-games"),
                network().getStatus());
        game.getArena().setPlayers(1, 2);
        assertEquals(2, network().getStatus().maxPlayers());
    }

    @Test
    void playersJoinTheGameWhenTheyJoinTheServer() {
        Game game = gameServer();
        PlayerMock steve = addPlayer("Steve");
        server.getScheduler().performOneTick();
        assertSame(game, plugin.getUser(steve).getGame());
        assertEquals(game.getWorld(), steve.getWorld());
    }

    @Test
    void gameServerTurnsAwayWhoCantPlayOrWatch() {
        config().set("survival-games.spectators", false);
        Game game = gameServer();
        assertTrue(login(UUID.randomUUID(), "Steve"), "the lobby is open");

        addPlayer("Steve");
        addPlayer("Alex");
        server.getScheduler().performOneTick();
        assertEquals(2, game.getParticipants().size());
        assertFalse(login(UUID.randomUUID(), "Other"), "full");
    }

    @Test
    void spectatorsGetOntoARunningGameServer() {
        Game game = gameServer();
        addPlayer("Steve");
        addPlayer("Alex");
        server.getScheduler().performOneTick();
        assertFalse(login(UUID.randomUUID(), "Other"), "the lobby is full");

        execute(addAdmin("Admin"), "sg game arena1 start");
        assertEquals(GameState.STARTING, game.getState());
        assertTrue(login(UUID.randomUUID(), "Other"), "can watch");
        PlayerMock other = addPlayer("Other");
        server.getScheduler().performOneTick();
        assertTrue(plugin.getUser(other).isSpectator());
    }

    @Test
    void leavingSendsToTheLobbyServer() {
        gameServer();
        PlayerMock steve = addPlayer("Steve");
        server.getScheduler().performOneTick();
        messages(steve);
        execute(steve, "sg leave");
        assertContains(messages(steve), "Connecting to lobby...");
    }

    @Test
    void noLobbyServerKicks() {
        Game game = gameServer();
        config().set("network.lobby-server", "");
        PlayerMock steve = addPlayer("Steve");
        server.getScheduler().performOneTick();
        game.kick(plugin.getUser(steve), net.kyori.adventure.text.Component.text("bye"));
        assertFalse(steve.isOnline());
    }

    @Test
    void lobbyServerHasNoGames() {
        createArena("arena1", GameType.SURVIVAL_GAMES, 2).setAutoGame(true);
        lobbyServer();
        plugin.getGameManager().createAutoGames();
        assertTrue(plugin.getGameManager().getGames().isEmpty());
        PlayerMock admin = addAdmin("Admin");
        execute(admin, "sg arena arena1 creategame");
        assertContains(messages(admin), "The lobby server of the network has no games");
    }

    @Test
    void lobbySendsToTheFullestWaitingGame() {
        lobbyServer();
        network().setStatus("sg-1", new ServerStatus(ServerStatus.State.LOBBY, 2, 12, "a", "survival-games"));
        network().setStatus("sg-2", new ServerStatus(ServerStatus.State.LOBBY, 5, 12, "b", "survival-games"));
        PlayerMock steve = addPlayer("Steve");
        execute(steve, "sg join");
        assertContains(messages(steve), "Connecting to sg-2...");

        network().setStatus("sg-2", new ServerStatus(ServerStatus.State.INGAME, 5, 12, "b", "survival-games"));
        execute(steve, "sg join");
        assertContains(messages(steve), "Connecting to sg-1...");
        execute(steve, "sg spectate");
        assertContains(messages(steve), "Connecting to sg-2...");
    }

    @Test
    void lobbyKnowsWhenThereIsNoGame() {
        lobbyServer();
        PlayerMock steve = addPlayer("Steve");
        execute(steve, "sg join");
        assertContains(messages(steve), "There is no game you can join right now");
        execute(steve, "sg join sg-1");
        assertContains(messages(steve), "The server sg-1 is not available right now.");
    }

    @Test
    void lobbyListsTheServers() {
        lobbyServer();
        network().setStatus("sg-1", new ServerStatus(ServerStatus.State.LOBBY, 2, 12, "castle", "survival-games"));
        PlayerMock steve = addPlayer("Steve");
        execute(steve, "sg list");
        var messages = messages(steve);
        assertContains(messages, "Games (2)");
        assertContains(messages, "sg-1 | Lobby | 2/12 | castle");
        assertContains(messages, "sg-2 | Offline");
    }

}
