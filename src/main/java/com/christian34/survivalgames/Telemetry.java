package com.christian34.survivalgames;

import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.game.Game;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Anonymous statistics (bStats, opt-out for all plugins in plugins/bStats/config.yml): the number of servers and
 * players, the server and Java version and a few settings of the plugin.
 *
 * @author Christian34
 */
public class Telemetry {
    /**
     * the plugin on bstats.org, 0: nothing is sent
     */
    static final int BSTATS_ID = 34655;
    private final SurvivalGames plugin;
    /**
     * finished games since the last bStats submission (every 30 minutes), per game type and per player count
     */
    private final AtomicInteger games = new AtomicInteger();
    private final Map<String, AtomicInteger> gameTypes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> gameSizes = new ConcurrentHashMap<>();

    public Telemetry(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // -Dsg.offline=true: nothing is sent (tests)
        if (UpdateChecker.isOffline() || BSTATS_ID <= 0) return;
        Metrics metrics = new Metrics(plugin, BSTATS_ID);
        var config = plugin.getFileManager().getConfigFile();
        metrics.addCustomChart(new SimplePie("network_mode", () -> lower(plugin.getNetworkManager().getMode().name())));
        metrics.addCustomChart(new SimplePie("language", () -> lower(config.getString("language"))));
        metrics.addCustomChart(new SimplePie("stats_storage", () ->
                config.getBoolean("stats.enabled") ? lower(config.getString("stats.storage")) : "disabled"));
        metrics.addCustomChart(new SimplePie("replays", () -> String.valueOf(config.getBoolean("replays.enabled"))));
        metrics.addCustomChart(new SimplePie("rewards", () -> String.valueOf(config.getBoolean("rewards.enabled"))));
        metrics.addCustomChart(new SimplePie("arenas", () -> bucket(plugin.getArenaManager().getArenas().size())));
        metrics.addCustomChart(new AdvancedPie("arena_types", this::arenaTypes));
        metrics.addCustomChart(new SingleLineChart("games_played", () -> games.getAndSet(0)));
        metrics.addCustomChart(new AdvancedPie("game_types_played", () -> drain(gameTypes)));
        metrics.addCustomChart(new AdvancedPie("players_per_game", () -> drain(gameSizes)));
    }

    /**
     * a game ended with a winner or a draw
     */
    public void gamePlayed(Game game) {
        games.incrementAndGet();
        gameTypes.computeIfAbsent(game.getType().getKey(), key -> new AtomicInteger()).incrementAndGet();
        gameSizes.computeIfAbsent(playerBucket(game.getParticipants().size()), key -> new AtomicInteger()).incrementAndGet();
    }

    /**
     * @return the number of arenas per game type
     */
    Map<String, Integer> arenaTypes() {
        Map<String, Integer> values = new HashMap<>();
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            values.merge(arena.getType().getKey(), 1, Integer::sum);
        }
        return values;
    }

    /**
     * @return the games since the last submission per game type, the counters start again at 0
     */
    Map<String, Integer> drainGameTypes() {
        return drain(gameTypes);
    }

    /**
     * @return the games since the last submission per player count, the counters start again at 0
     */
    Map<String, Integer> drainGameSizes() {
        return drain(gameSizes);
    }

    static Map<String, Integer> drain(Map<String, AtomicInteger> counters) {
        Map<String, Integer> values = new HashMap<>();
        counters.forEach((key, count) -> {
            int value = count.getAndSet(0);
            if (value > 0) values.put(key, value);
        });
        return values;
    }

    static String playerBucket(int players) {
        if (players <= 2) return "2";
        if (players <= 4) return "3-4";
        if (players <= 8) return "5-8";
        if (players <= 16) return "9-16";
        if (players <= 24) return "17-24";
        return "25+";
    }

    static String bucket(int amount) {
        if (amount <= 3) return Integer.toString(amount);
        if (amount <= 10) return "4-10";
        return "10+";
    }

    private static String lower(String value) {
        return String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

}
