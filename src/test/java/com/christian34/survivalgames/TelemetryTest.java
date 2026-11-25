package com.christian34.survivalgames;

import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.User;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The anonymous statistics of bStats: what the charts get. Error reports only contain errors of SurvivalGames and
 * nothing about the server or its players.
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
        assertTrue(plugin.getTelemetry().counts(Telemetry.Chart.GAME_TYPES).isEmpty(), "the game isn't over yet");

        alex.setKiller(steve);
        alex.setHealth(0);
        assertEquals(GameState.ENDED, game.getState());
        assertEquals(Map.of("survival-games", 1), plugin.getTelemetry().counts(Telemetry.Chart.GAME_TYPES));
        assertEquals(Map.of("2", 1), plugin.getTelemetry().counts(Telemetry.Chart.GAME_SIZES));
        assertEquals(Map.of("survival-games", 1), plugin.getTelemetry().counts(Telemetry.Chart.GAME_TYPES), "the counts stay after a submission");
        assertEquals(Map.of("< 5 min", 1), plugin.getTelemetry().counts(Telemetry.Chart.DURATIONS));
        assertEquals(Map.of("before the deathmatch", 1), plugin.getTelemetry().counts(Telemetry.Chart.ENDINGS));
        assertEquals(Map.of("command or menu", 2), plugin.getTelemetry().counts(Telemetry.Chart.JOINS));
    }

    @Test
    void theSetupOfTheServerIsDescribed() {
        Telemetry telemetry = plugin.getTelemetry();
        assertEquals("none", telemetry.usedGameTypes());
        assertTrue(telemetry.varoRules().isEmpty(), "no Varo on this server");
        createArena("arena1", GameType.SURVIVAL_GAMES, 4);
        assertEquals("survival-games", telemetry.usedGameTypes());
        createArena("varo1", GameType.VARO, 4);
        assertEquals("survival-games + varo", telemetry.usedGameTypes());
        assertEquals(Map.of("survival-games", Map.of("1", 1), "varo", Map.of("2", 1)), telemetry.teamSizes());

        // the defaults of config.yml
        Map<String, Map<String, Integer>> modules = telemetry.modules();
        assertEquals(Map.of("survival-games", 1), modules.get("kits"));
        assertEquals(Map.of("varo", 1), modules.get("backpack"));
        assertEquals(Map.of("survival-games + varo + uhc", 1), modules.get("bounty"));
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
        assertEquals(Map.of("kit chosen", 2, "replay watched", 1), telemetry.counts(Telemetry.Chart.FEATURES));
        assertEquals("1-3 days", Telemetry.dayBucket(1));
        assertEquals("8-14 days", Telemetry.dayBucket(14));
        assertEquals("30+ min", Telemetry.minuteBucket(30));
    }

    // ---------------------------------------------------------------- error reports

    private static Throwable thrownAt(String className) {
        RuntimeException exception = new RuntimeException("test");
        exception.setStackTrace(new StackTraceElement[]{
                new StackTraceElement(className, "run", "Test.java", 1),
                new StackTraceElement("org.bukkit.Server", "tick", "Server.java", 2)});
        return exception;
    }

    @Test
    void testsSendNothing() {
        assertTrue(UpdateChecker.isOffline(), "the tests must not send statistics or error reports");
    }

    @Test
    void onlyOwnErrorsAreReported() {
        assertTrue(Telemetry.isOwnError(thrownAt("com.christian34.survivalgames.game.Game")));
        assertFalse(Telemetry.isOwnError(thrownAt("com.example.OtherPlugin")));
        assertFalse(Telemetry.isOwnError(thrownAt("com.christian34.survivalgames.libs.cloud.CommandManager")),
                "errors of the bundled libraries alone aren't ours");
        // the cause counts too
        RuntimeException wrapped = new RuntimeException("wrapper", thrownAt("com.christian34.survivalgames.game.Game"));
        wrapped.setStackTrace(new StackTraceElement[]{new StackTraceElement("org.bukkit.Server", "tick", "Server.java", 1)});
        assertTrue(Telemetry.isOwnError(wrapped));
    }

    @Test
    void sameErrorHasSameSignature() {
        Throwable first = thrownAt("com.christian34.survivalgames.game.Game");
        Throwable second = thrownAt("com.christian34.survivalgames.game.Game");
        assertEquals(Telemetry.signature(first), Telemetry.signature(second));
        assertNotEquals(Telemetry.signature(first), Telemetry.signature(thrownAt("com.christian34.survivalgames.game.VaroGame")));
    }

    @Test
    void playersAndUuidsAreRemoved() {
        PlayerMock steve = addPlayer("Steve");
        String text = "Couldn't teleport Steve (" + steve.getUniqueId() + ")";
        assertEquals("Couldn't teleport <player> (<uuid>)", Telemetry.anonymize(text));
    }

    @Test
    void eventsAreMinimized() {
        PlayerMock steve = addPlayer("Steve");
        SentryEvent event = new SentryEvent();
        User user = new User();
        user.setIpAddress("1.2.3.4");
        event.setUser(user);
        event.setServerName("my-server.example.com");
        SentryException exception = new SentryException();
        exception.setValue("Steve has no team");
        event.setExceptions(List.of(exception));
        Message message = new Message();
        message.setFormatted("player " + steve.getUniqueId());
        event.setMessage(message);

        SentryEvent minimized = new Telemetry(plugin).minimize(event);
        assertNull(minimized.getUser());
        assertNull(minimized.getServerName());
        assertEquals("<player> has no team", minimized.getExceptions().getFirst().getValue());
        assertEquals("player <uuid>", minimized.getMessage().getFormatted());
    }

    @Test
    void debugErrorsReachTheReportHandler() {
        // Telemetry listens on the plugin's logger, Debug must log there (printStackTrace would bypass it)
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        plugin.getLogger().addHandler(handler);
        try {
            Debug.warn("Couldn't do something", new IllegalStateException("test"));
            Debug.warn("Expected problem");
        } finally {
            plugin.getLogger().removeHandler(handler);
        }
        assertEquals(2, records.size());
        Telemetry telemetry = new Telemetry(plugin);
        assertTrue(telemetry.shouldReport(records.get(0)));
        assertFalse(telemetry.shouldReport(records.get(0)), "every error is reported once");
        assertFalse(telemetry.shouldReport(records.get(1)), "warnings without an exception aren't reported");
    }

    @Test
    void attachedConfigHasNoSecrets() {
        PlayerMock steve = addPlayer("Steve");
        YamlConfiguration data = new YamlConfiguration();
        data.set("stats.mysql.url", "jdbc:mysql://db.example.com:3306/minecraft");
        data.set("stats.mysql.user", "minecraft");
        data.set("stats.mysql.password", "hunter2");
        data.set("discord.webhook-url", "https://discord.com/api/webhooks/1/abc");
        data.set("discord.username", "");
        data.set("network.servers.sg1.address", "10.0.0.5:25566");
        data.set("network.lobby", "play.example.com:25565");
        data.set("varo.play-time", 15);
        data.set("varo.strikes", List.of("warning", "Steve was here"));
        data.set("language", "de");
        data.set("modules.equip.enabled", true);
        data.set("owner", steve.getUniqueId().toString());

        YamlConfiguration redacted = YamlConfiguration.loadConfiguration(new StringReader(Telemetry.redact(data)));
        for (String key : List.of("stats.mysql.url", "stats.mysql.user", "stats.mysql.password", "discord.webhook-url",
                "network.servers.sg1.address")) {
            assertEquals(Telemetry.REDACTED, redacted.getString(key), key);
        }
        assertEquals(Telemetry.REDACTED, redacted.getString("network.lobby"), "addresses under any key");
        assertEquals("", redacted.getString("discord.username"), "empty values show that nothing is set");
        assertEquals(15, redacted.getInt("varo.play-time"));
        assertEquals(List.of("warning", "<player> was here"), redacted.getStringList("varo.strikes"));
        assertEquals("de", redacted.getString("language"));
        assertTrue(redacted.getBoolean("modules.equip.enabled"));
        assertEquals("<uuid>", redacted.getString("owner"));
    }

    @Test
    void defaultConfigHasNoSecretsLeft() {
        String redacted = Telemetry.redact(plugin.getFileManager().getConfigFile().getData());
        assertFalse(redacted.contains("localhost"), redacted);
        assertTrue(redacted.contains("sentry:"), "the settings are there");
    }

    @Test
    void reportsContainTheServerState() {
        addPlayer("Steve");
        addPlayer("Alex");
        createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        Map<String, Object> state = plugin.getTelemetry().serverState();
        assertEquals(2, state.get("players_online"));
        assertEquals(0, state.get("running_games"));
        assertEquals(Map.of("survival-games lobby", 1), state.get("games"));
        assertTrue((Long) state.get("memory_max_mb") > 0);
        assertTrue(state.containsKey("worlds"));
    }

    @Test
    void stateIsGroupedForTags() {
        assertEquals("0", Telemetry.countBucket(0));
        assertEquals("1-5", Telemetry.countBucket(5));
        assertEquals("11-25", Telemetry.countBucket(24));
        assertEquals("100+", Telemetry.countBucket(150));
        assertEquals("19.5+", Telemetry.tpsBucket(20.0));
        assertEquals("15-18", Telemetry.tpsBucket(16.3));
        assertEquals("< 10", Telemetry.tpsBucket(4.2));
    }

    @Test
    void testsDontStartSentry() {
        assertFalse(plugin.getTelemetry().sendTestReport(), "/sg debug sentry sends nothing in the tests");
        assertFalse(plugin.getTelemetry().sendFeedback("The game doesn't start"), "/sg report neither");
        // breadcrumbs without Sentry are ignored
        plugin.getTelemetry().breadcrumb("test", "nothing happens");
    }

    @Test
    void reportsContainTheGame() {
        addPlayer("Steve");
        Game game = createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        Map<String, Object> context = Telemetry.gameContext(game);
        assertEquals("survival-games", context.get("type"));
        assertEquals("arena1", context.get("arena"));
        assertEquals("lobby", context.get("phase"));
        assertEquals(0, context.get("running_seconds"));
        assertTrue(context.containsKey("participants"));
        assertTrue(context.containsKey("team_size"));
        Map<String, Object> games = plugin.getTelemetry().gamesContext();
        assertEquals(1, games.get("count"));
        assertTrue(String.valueOf(games.get("survival-games arena1")).startsWith("lobby, 0 s"),
                String.valueOf(games));
    }

    @Test
    void errorsInAGameKnowTheGame() {
        Game game = createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        List<Game> seen = new ArrayList<>();
        Telemetry.inGame(game, () -> {
            // the report handler runs on the same thread
            seen.add(Telemetry.currentGame());
            Telemetry.inGame(game, () -> { });
            seen.add(Telemetry.currentGame());
        });
        assertEquals(List.of(game, game), seen);
        assertNull(Telemetry.currentGame());
    }

    @Test
    void reportsContainServerPluginsAndSystem() {
        Map<String, Object> server = plugin.getTelemetry().serverInfo();
        assertEquals(false, server.get("late_load"));
        assertTrue(server.containsKey("version"));
        assertTrue(server.containsKey("uptime_minutes"));
        assertTrue(Telemetry.pluginsContext().containsKey("SurvivalGames"));
        Map<String, Object> system = Telemetry.systemInfo();
        assertTrue((Integer) system.get("cpu_cores") > 0);
        assertEquals(System.getProperty("java.vendor"), system.get("java_vendor"));
    }

    @Test
    void commandsWithoutArguments() {
        assertEquals("sg join", Telemetry.sgCommand("/sg join arena1 Steve"));
        assertEquals("sg admin", Telemetry.sgCommand("/survivalgames:SG ADMIN"));
        assertEquals("sg", Telemetry.sgCommand("varo"));
        assertNull(Telemetry.sgCommand("/sgx"));
        assertNull(Telemetry.sgCommand("/msg Steve hi"));
    }

    @Test
    void lagsAreOnlyReportedInOwnCode() {
        StackTraceElement own = new StackTraceElement(Telemetry.PACKAGE + "game.Game", "tick", "Game.java", 1);
        StackTraceElement library = new StackTraceElement(Telemetry.PACKAGE + "libs.sentry.Sentry", "x", "S.java", 1);
        StackTraceElement server = new StackTraceElement("net.minecraft.server.MinecraftServer", "tick", "M.java", 1);
        assertTrue(Telemetry.hasOwnFrame(new StackTraceElement[]{server, own}));
        assertFalse(Telemetry.hasOwnFrame(new StackTraceElement[]{server, library}));
        var lag = new Telemetry.ServerThreadBlocked(1500, false, new StackTraceElement[]{own, server});
        assertEquals("The server thread was blocked for 1500 ms", lag.getMessage());
        assertTrue(Telemetry.signature(lag).endsWith("Game.tick(Game.java:1)"));
    }

    @Test
    void lagsNameTheLineWithMostSamples() {
        StackTraceElement server = new StackTraceElement("net.minecraft.server.MinecraftServer", "tick", "M.java", 1);
        StackTraceElement tick = new StackTraceElement(Telemetry.PACKAGE + "game.Game", "tick", "Game.java", 1);
        StackTraceElement load = new StackTraceElement(Telemetry.PACKAGE + "arena.GameWorld", "load", "GameWorld.java", 150);
        StackTraceElement world = new StackTraceElement("org.bukkit.Bukkit", "createWorld", "Bukkit.java", 1);
        List<StackTraceElement[]> samples = List.of(
                new StackTraceElement[]{tick, server},
                new StackTraceElement[]{world, load, server},
                new StackTraceElement[]{world, load, server},
                new StackTraceElement[]{server});
        Telemetry.LagCulprit culprit = Telemetry.culprit(samples);
        assertNotNull(culprit);
        assertEquals(load.toString(), culprit.frame());
        assertEquals(4, culprit.samples());
        assertEquals(3, culprit.inPlugin());
        assertEquals(2, culprit.atFrame());
        assertSame(samples.get(1), culprit.stack());
    }

    @Test
    void lagsMostlyOutsideThePluginAreNotReported() {
        StackTraceElement server = new StackTraceElement("net.minecraft.server.MinecraftServer", "tick", "M.java", 1);
        StackTraceElement member = new StackTraceElement(Telemetry.PACKAGE + "game.Game", "isMember", "Game.java", 274);
        // e.g. the JVM linking a lambda for a moment during a long garbage collection
        assertNull(Telemetry.culprit(List.of(new StackTraceElement[]{member, server}, new StackTraceElement[]{server},
                new StackTraceElement[]{server})));
        assertNull(Telemetry.culprit(List.of()));
    }

}
