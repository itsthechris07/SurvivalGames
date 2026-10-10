package com.christian34.survivalgames.stats;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.SurvivalGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Elo rating (stats.rating): the change of a game depends on the ratings of the opponents, a round of Survival
 * Games changes the ratings of its players.
 *
 * @author Christian34
 */
class RatingTest extends PluginTestBase {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);
    private static final UUID D = new UUID(0, 4);
    private SurvivalGame game;
    private PlayerMock steve;
    private PlayerMock alex;

    @BeforeEach
    void setUpGame() {
        plugin.getStats().setExecutor(Runnable::run);
        config().set("survival-games.lobby-countdown", 1);
        config().set("survival-games.start-countdown", 1);
        config().set("survival-games.grace-period", 0);
        game = (SurvivalGame) createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
    }

    /**
     * Steve kills Alex and wins
     */
    private void playRound() {
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        for (int tick = 0; tick < 20 * 10 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());
        messages(steve);
        messages(alex);
        alex.setKiller(steve);
        alex.setHealth(0);
        assertEquals(GameState.ENDED, game.getState());
    }

    @Test
    void equalPlayersWinAndLoseHalfOfTheFactor() {
        Map<UUID, Integer> changes = Rating.changes(Map.of(A, 2, B, 1), Map.of(A, 1000, B, 1000), 32);
        assertEquals(16, changes.get(A));
        assertEquals(-16, changes.get(B));
    }

    @Test
    void beatingAStrongPlayerBringsMore() {
        Map<UUID, Integer> favourite = Rating.changes(Map.of(A, 2, B, 1), Map.of(A, 1400, B, 1000), 32);
        assertEquals(3, favourite.get(A));
        assertEquals(-3, favourite.get(B));
        Map<UUID, Integer> upset = Rating.changes(Map.of(A, 1, B, 2), Map.of(A, 1400, B, 1000), 32);
        assertEquals(-29, upset.get(A));
        assertEquals(29, upset.get(B));
    }

    @Test
    void aGameWithManyPlayersCountsLikeOneDuel() {
        // D was out first, A has won
        Map<UUID, Integer> changes = Rating.changes(Map.of(A, 4, B, 3, C, 2, D, 1), Map.of(), 32);
        assertEquals(16, changes.get(A));
        assertEquals(5, changes.get(B));
        assertEquals(-5, changes.get(C));
        assertEquals(-16, changes.get(D));
        assertEquals(0, changes.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void teammatesArentCompared() {
        // A and B have won together against C
        Map<UUID, Integer> changes = Rating.changes(Map.of(A, 2, B, 2, C, 1), Map.of(), 32);
        assertEquals(16, changes.get(A));
        assertEquals(16, changes.get(B));
        assertEquals(-16, changes.get(C));
        assertEquals(0, Rating.changes(Map.of(A, 1), Map.of(), 32).get(A), "nobody to compare with");
    }

    @Test
    void aRoundChangesTheRatings() {
        assertEquals(1000, plugin.getStats().getRating(steve.getUniqueId(), GameType.SURVIVAL_GAMES));
        playRound();
        assertContains(messages(steve), "Your rating: 1016 (+16)");
        assertContains(messages(alex), "Your rating: 984 (-16)");
        assertEquals(1016, plugin.getStats().getRating(steve.getUniqueId(), GameType.SURVIVAL_GAMES));
        assertEquals(984, plugin.getStats().getRating(alex.getUniqueId(), GameType.SURVIVAL_GAMES));
        assertEquals(1000, plugin.getStats().getRating(steve.getUniqueId(), GameType.VARO), "per game type");

        execute(alex, "sg stats Steve");
        assertContains(messages(alex), "Rating: 1016");
        execute(alex, "sg top rating");
        assertContains(messages(alex), "#1 Steve - 1016");
    }

    @Test
    void theFactorLimitsTheChange() {
        config().set("stats.rating.k-factor", 10);
        playRound();
        assertContains(messages(steve), "Your rating: 1005 (+5)");
    }

    @Test
    void ratingCanBeTurnedOff() {
        config().set("stats.rating.enabled", false);
        assertFalse(plugin.getStats().isRated(GameType.SURVIVAL_GAMES));
        playRound();
        assertNotContains(messages(steve), "Your rating");
        assertEquals(1000, plugin.getStats().getRating(steve.getUniqueId(), GameType.SURVIVAL_GAMES));
        execute(alex, "sg stats Steve");
        assertNotContains(messages(alex), "Rating:");
    }

    @Test
    void onlyTheGameTypesOfTheConfigAreRated() {
        assertTrue(plugin.getStats().isRated(GameType.SURVIVAL_GAMES));
        assertFalse(plugin.getStats().isRated(GameType.VARO));
        config().set("stats.rating.games", List.of("varo"));
        assertTrue(plugin.getStats().isRated(GameType.VARO));
        playRound();
        assertNotContains(messages(steve), "Your rating");
    }

}
