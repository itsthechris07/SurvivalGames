package com.christian34.survivalgames.game;

import com.christian34.survivalgames.FakeForms;
import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.lib.Items;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The players in the lobby of a round of Survival Games vote for the map and move into the arena that has won.
 *
 * @author Christian34
 */
class MapVotingTest extends PluginTestBase {
    private Arena arena2;
    private SurvivalGame game;
    private PlayerMock steve;
    private PlayerMock alex;

    @BeforeEach
    void setUpArenas() {
        config().set("survival-games.lobby-countdown", 15);
        config().set("survival-games.start-countdown", 1);
        config().set("survival-games.map-voting.end-seconds", 10);
        // the maps are chosen when the game is created
        arena2 = createArena("arena2", GameType.SURVIVAL_GAMES, 4);
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
    }

    private void createGame() {
        game = (SurvivalGame) createGame("arena1", GameType.SURVIVAL_GAMES, 4);
    }

    private void join() {
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        assertSame(game, plugin.getUser(alex).getGame());
    }

    private Game gameOf(PlayerMock player) {
        return plugin.getUser(player).getGame();
    }

    @Test
    void playersVoteInTheLobby() {
        createGame();
        execute(steve, "sg join arena1");
        assertContains(messages(steve), "Vote for the map: /sg vote");
        assertTrue(Items.is(steve.getInventory().getItem(2), Items.VOTE), "the map item in the lobby");
        MapVoting voting = game.getMapVoting();
        assertEquals(List.of("arena1", "arena2"), voting.getOptions().stream().map(Arena::getName).toList());

        execute(steve, "sg vote arena2");
        assertContains(messages(steve), "You voted for arena2. Change your vote: /sg vote");
        assertEquals(1, voting.getVotes(arena2));
        execute(steve, "sg vote arena1");
        assertEquals(0, voting.getVotes(arena2), "one vote per player");
        assertEquals(1, voting.getVotes(game.getArena()));
    }

    @Test
    void everybodyMovesIntoTheMapThatHasWon() {
        createGame();
        join();
        execute(steve, "sg vote arena2");
        execute(alex, "sg vote arena2");
        messages(steve);
        seconds(4);
        assertSame(game, gameOf(steve), "the votes are counted 10 seconds before the start");

        seconds(2);
        assertContains(messages(steve), "The map arena2 has won with 2 votes!");
        Game next = gameOf(steve);
        assertNotSame(game, next);
        assertSame(arena2, next.getArena());
        assertSame(next, gameOf(alex));
        assertEquals(GameState.LOBBY, next.getState());
        assertEquals(2, next.getParticipants().size());
        assertEquals(next.getWorld(), steve.getWorld());
        assertTrue(game.getMembers().isEmpty());
        assertNull(plugin.getGameManager().getGame(game.getArena()), "the empty game is gone");
        assertTrue(next.getCountdown() > 0 && next.getCountdown() <= 10, "the countdown goes on: " + next.getCountdown());
        assertFalse(Items.is(steve.getInventory().getItem(2), Items.VOTE), "no second voting");

        for (int tick = 0; tick < 20 * 15 && next.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, next.getState());
    }

    @Test
    void arenasWithAutoGameGetANewGame() {
        createGame();
        game.getArena().setAutoGame(true);
        join();
        execute(steve, "sg vote arena2");
        seconds(6);
        Game fresh = plugin.getGameManager().getGame(game.getArena());
        assertNotNull(fresh);
        assertNotSame(game, fresh);
        assertTrue(fresh.getMembers().isEmpty());
    }

    @Test
    void aTieKeepsTheArenaOfTheGame() {
        createGame();
        join();
        execute(steve, "sg vote arena2");
        execute(alex, "sg vote arena1");
        messages(steve);
        seconds(6);
        assertContains(messages(steve), "The map arena1 has won with 1 votes!");
        assertSame(game, gameOf(steve));
        assertNull(game.getMapVoting(), "the voting is over");
        execute(steve, "sg vote arena2");
        assertContains(messages(steve), "You can't vote for a map right now.");
    }

    @Test
    void withoutVotesNobodyMoves() {
        createGame();
        join();
        seconds(6);
        assertSame(game, gameOf(steve));
    }

    @Test
    void permissionsGiveMoreVotes() {
        createGame();
        PlayerMock vip = addPlayer("Vip", "sg.vote.3");
        join();
        execute(vip, "sg join arena1");
        execute(steve, "sg vote arena1");
        execute(alex, "sg vote arena1");
        execute(vip, "sg vote arena2");
        assertEquals(3, game.getMapVoting().getVotes(arena2));
        messages(vip);
        seconds(6);
        assertContains(messages(vip), "The map arena2 has won with 3 votes!");
        assertSame(arena2, gameOf(vip).getArena());
    }

    @Test
    void votesOfPlayersWhoLeftDontCount() {
        createGame();
        PlayerMock bob = addPlayer("Bob");
        join();
        execute(bob, "sg join arena1");
        execute(bob, "sg vote arena2");
        execute(bob, "sg leave");
        assertEquals(0, game.getMapVoting().getVotes(arena2));
    }

    @Test
    void onlyTheMapsOfTheVotingCanBeChosen() {
        createArena("varo1", GameType.VARO, 2);
        createGame();
        // created after the game: not one of its maps
        createArena("arena3", GameType.SURVIVAL_GAMES, 4);
        execute(steve, "sg join arena1");
        messages(steve);
        execute(steve, "sg vote varo1");
        assertContains(messages(steve), "You can't vote for varo1.");
        execute(steve, "sg vote arena3");
        assertContains(messages(steve), "You can't vote for arena3.");
        execute(steve, "sg vote nope");
        assertNull(game.getMapVoting().getVote(steve.getUniqueId()));
    }

    @Test
    void mapsWithPlayersInTheLobbyCantWin() {
        createGame();
        join();
        execute(steve, "sg vote arena2");
        execute(alex, "sg vote arena2");
        // somebody else waits there in the meantime
        Game other = plugin.getGameManager().createGame(arena2);
        execute(addPlayer("Bob"), "sg join arena2");
        seconds(6);
        assertSame(game, gameOf(steve));
        assertEquals(1, other.getParticipants().size());
    }

    @Test
    void theNumberOfMapsIsLimited() {
        config().set("survival-games.map-voting.maps", 2);
        createArena("arena3", GameType.SURVIVAL_GAMES, 4);
        createArena("arena4", GameType.SURVIVAL_GAMES, 4);
        createGame();
        List<Arena> options = game.getMapVoting().getOptions();
        assertEquals(2, options.size());
        assertSame(game.getArena(), options.getFirst(), "the arena of the game is one of them");
    }

    @Test
    void arenasWithAnotherTeamSizeArentOffered() {
        arena2.setTeamSize(2);
        createGame();
        assertNull(game.getMapVoting(), "nothing to choose from");
        execute(steve, "sg join arena1");
        assertFalse(Items.is(steve.getInventory().getItem(2), Items.VOTE));
        assertNotContains(messages(steve), "Vote for the map");
        execute(steve, "sg vote");
        assertContains(messages(steve), "You can't vote for a map right now.");
    }

    @Test
    void votingCanBeTurnedOff() {
        config().set("survival-games.map-voting.enabled", false);
        createGame();
        assertNull(game.getMapVoting());
    }

    @Test
    void votingMenu() {
        createGame();
        FakeForms forms = new FakeForms();
        plugin.setBedrockForms(forms);
        join();
        execute(alex, "sg vote arena2");
        execute(steve, "sg vote");
        assertEquals(List.of("arena1", "arena2"), forms.buttons());
        assertContains(forms.lore("arena2"), "Votes: 1");
        assertContains(forms.lore("arena2"), "Players: 2-4");
        assertContains(forms.lore("arena2"), "Click to vote");

        forms.click(steve, "arena2");
        assertEquals(2, game.getMapVoting().getVotes(arena2));
        execute(steve, "sg vote");
        assertContains(forms.lore("arena2"), "Your vote");
    }

    @Test
    void startingTheGameCountsTheVotesFirst() {
        createGame();
        PlayerMock admin = addAdmin("Admin");
        execute(admin, "sg join arena1");
        execute(steve, "sg join arena1");
        execute(steve, "sg vote arena2");
        execute(admin, "sg start");
        Game next = gameOf(admin);
        assertSame(arena2, next.getArena());
        assertEquals(GameState.STARTING, next.getState());
        assertSame(next, gameOf(steve));
    }

    @Test
    void theMapOfTheLastRoundIsBlocked() {
        createGame();
        Arena arena3 = createArena("arena3", GameType.SURVIVAL_GAMES, 4);
        game = (SurvivalGame) plugin.getGameManager().createGame(arena3);
        plugin.getGameManager().setLastPlayed(arena2);
        MapVoting voting = game.getMapVoting();
        assertEquals(List.of("arena3", "arena1"), voting.getOptions().stream().map(Arena::getName).toList());
        execute(steve, "sg join arena3");
        messages(steve);
        execute(steve, "sg vote arena2");
        assertContains(messages(steve), "You can't vote for arena2.");
        assertEquals(0, voting.getVotes(arena2));

        // another round has been played somewhere else
        plugin.getGameManager().setLastPlayed(game.getArena());
        assertEquals(List.of("arena1", "arena2"), voting.getOptions().stream().map(Arena::getName).sorted().toList(),
                "the own arena is blocked too");
    }

    @Test
    void theLastMapStaysIfItIsTheOnlyOne() {
        createGame();
        plugin.getGameManager().setLastPlayed(arena2);
        assertEquals(List.of("arena1"), game.getMapVoting().getOptions().stream().map(Arena::getName).toList());
        plugin.getGameManager().setLastPlayed(game.getArena());
        assertEquals(List.of("arena2"), game.getMapVoting().getOptions().stream().map(Arena::getName).toList());
    }

    @Test
    void theBeginningRoundIsRemembered() {
        createGame();
        assertNull(plugin.getGameManager().getLastPlayed());
        join();
        for (int tick = 0; tick < 20 * 30 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());
        assertEquals("arena1", plugin.getGameManager().getLastPlayed());
    }

    @Test
    void blockingTheLastMapCanBeTurnedOff() {
        config().set("survival-games.map-voting.block-last", false);
        createGame();
        plugin.getGameManager().setLastPlayed(arena2);
        assertEquals(List.of("arena1", "arena2"), game.getMapVoting().getOptions().stream().map(Arena::getName).toList());
    }

}
