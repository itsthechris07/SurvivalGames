package com.christian34.survivalgames;

import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The anonymous statistics of bStats: what the charts get.
 *
 * @author Christian34
 */
class TelemetryTest extends PluginTestBase {

    @Test
    void playerCountsAreGrouped() {
        assertEquals("2", Telemetry.playerBucket(2));
        assertEquals("3-4", Telemetry.playerBucket(3));
        assertEquals("5-8", Telemetry.playerBucket(8));
        assertEquals("9-16", Telemetry.playerBucket(9));
        assertEquals("17-24", Telemetry.playerBucket(24));
        assertEquals("25+", Telemetry.playerBucket(100));
        assertEquals("0", Telemetry.bucket(0));
        assertEquals("3", Telemetry.bucket(3));
        assertEquals("4-10", Telemetry.bucket(10));
        assertEquals("10+", Telemetry.bucket(11));
    }

    @Test
    void arenasAreCountedPerGameType() {
        createArena("arena1", GameType.SURVIVAL_GAMES, 4);
        createArena("arena2", GameType.SURVIVAL_GAMES, 4);
        createArena("varo1", GameType.VARO, 4);
        assertEquals(Map.of("survival-games", 2, "varo", 1), plugin.getTelemetry().arenaTypes());
    }

    @Test
    void finishedGamesAreCountedOnce() {
        config().set("survival-games.lobby-countdown", 1);
        config().set("survival-games.start-countdown", 1);
        config().set("survival-games.grace-period", 0);
        config().set("survival-games.deathmatch.players", 0);
        Game game = createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        PlayerMock steve = addPlayer("Steve");
        PlayerMock alex = addPlayer("Alex");
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        for (int tick = 0; tick < 20 * 10 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());
        assertTrue(plugin.getTelemetry().drain(Telemetry.Chart.GAME_TYPES).isEmpty(), "the game isn't over yet");

        alex.setKiller(steve);
        alex.setHealth(0);
        assertEquals(GameState.ENDED, game.getState());
        assertEquals(Map.of("survival-games", 1), plugin.getTelemetry().drain(Telemetry.Chart.GAME_TYPES));
        assertEquals(Map.of("2", 1), plugin.getTelemetry().drain(Telemetry.Chart.GAME_SIZES));
        assertTrue(plugin.getTelemetry().drain(Telemetry.Chart.GAME_TYPES).isEmpty(), "the counters start again after a submission");
        assertEquals(Map.of("< 5 min", 1), plugin.getTelemetry().drain(Telemetry.Chart.DURATIONS));
        assertEquals(Map.of("before the deathmatch", 1), plugin.getTelemetry().drain(Telemetry.Chart.ENDINGS));
        assertEquals(Map.of("command or menu", 2), plugin.getTelemetry().drain(Telemetry.Chart.JOINS));
    }

    @Test
    void theSetupOfTheServerIsDescribed() {
        Telemetry telemetry = plugin.getTelemetry();
        assertEquals("none", telemetry.usedGameTypes());
        assertTrue(telemetry.varoRules().isEmpty(), "no Varo on this server");
        createArena("arena1", GameType.SURVIVAL_GAMES, 4);
        assertEquals("survival-games", telemetry.usedGameTypes());
        createArena("varo1", GameType.VARO, 4);
        assertEquals("both", telemetry.usedGameTypes());
        assertEquals(Map.of("survival-games", Map.of("1", 1), "varo", Map.of("2", 1)), telemetry.teamSizes());

        // the defaults of config.yml
        Map<String, Map<String, Integer>> modules = telemetry.modules();
        assertEquals(Map.of("survival-games", 1), modules.get("kits"));
        assertEquals(Map.of("varo", 1), modules.get("backpack"));
        assertEquals(Map.of("both", 1), modules.get("bounty"));
        config().set("modules.bounty.enabled", false);
        assertEquals(Map.of("disabled", 1), telemetry.modules().get("bounty"));

        assertTrue(telemetry.varoRules().containsKey("backups"));
        assertFalse(telemetry.varoRules().containsKey("peaceful days"), "optional rules are off by default");
        config().set("varo.peaceful-days", 3);
        assertEquals(1, telemetry.varoRules().get("peaceful days"));
    }

    @Test
    void usedFeaturesAreCounted() {
        Telemetry telemetry = plugin.getTelemetry();
        telemetry.used(Telemetry.Feature.KIT);
        telemetry.used(Telemetry.Feature.KIT);
        telemetry.used(Telemetry.Feature.REPLAY);
        assertEquals(Map.of("kit chosen", 2, "replay watched", 1), telemetry.drain(Telemetry.Chart.FEATURES));
        assertEquals("1-3 days", Telemetry.dayBucket(1));
        assertEquals("8-14 days", Telemetry.dayBucket(14));
        assertEquals("30+ min", Telemetry.minuteBucket(30));
    }

}
