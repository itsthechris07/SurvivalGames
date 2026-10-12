package com.christian34.survivalgames;

import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.files.ConfigFile;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.game.VaroGame;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Anonymous statistics (bStats, opt-out for all plugins in plugins/bStats/config.yml): the number of servers and
 * players, the server and Java version, a few settings of the plugin and how often its features are used. No names
 * (players, arenas, kits, teams) are sent.
 *
 * @author Christian34
 */
public class Telemetry {
    /**
     * the plugin on bstats.org, 0: nothing is sent
     */
    static final int BSTATS_ID = 34655;
    /**
     * the optional plugins (softdepend in plugin.yml)
     */
    private static final List<String> INTEGRATIONS = List.of("Vault", "PlaceholderAPI", "MiniPlaceholders", "Parties",
            "Spigot-Party-API-PAF", "Citizens", "FancyNpcs", "floodgate", "TAB", "Essentials", "Multiverse-Core",
            "Multiverse-Inventories");
    private final SurvivalGames plugin;
    /**
     * since the last bStats submission (every 30 minutes): the finished games (per game type, player count, length and
     * ending), the features used and the ways the players joined
     */
    private final AtomicInteger games = new AtomicInteger();
    private final Map<String, AtomicInteger> gameTypes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> gameSizes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> durations = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> endings = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> varoLengths = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> features = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> joins = new ConcurrentHashMap<>();

    /**
     * something the players or admins do in a game
     */
    public enum Feature {
        KIT("kit chosen"),
        MAP_VOTE("map vote"),
        TEAM_CREATED("team created"),
        SPECTATE("game watched"),
        DEATHMATCH("deathmatch"),
        SUPPLY_DROP("supply drop"),
        BOUNTY("bounty"),
        SPONSOR("sponsored item"),
        INVENTORY_VIEW("inventory viewed"),
        BACKPACK("backpack opened"),
        CHEST_LOCK("chest locked"),
        GRAVITY_GRENADE("gravity grenade"),
        TOXIC_RAIN("toxic rain"),
        OFFLINE_NPC("offline npc"),
        STRIKE("strike"),
        REPLAY("replay watched");

        private final String label;

        Feature(String label) {
            this.label = label;
        }
    }

    /**
     * how a player gets into a game
     */
    public enum Join {
        COMMAND("command or menu"),
        SIGN("sign"),
        NPC("npc"),
        PARTY("party"),
        NETWORK("network");

        private final String label;

        Join(String label) {
            this.label = label;
        }
    }

    public Telemetry(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // -Dsg.offline=true: nothing is sent (tests)
        if (UpdateChecker.isOffline() || BSTATS_ID <= 0) return;
        Metrics metrics = new Metrics(plugin, BSTATS_ID);
        // the setup of the server
        metrics.addCustomChart(new SimplePie("network_mode", () -> lower(plugin.getNetworkManager().getMode().name())));
        metrics.addCustomChart(new SimplePie("language", () -> lower(config().getString("language"))));
        metrics.addCustomChart(new SimplePie("stats_storage", () ->
                config().getBoolean("stats.enabled") ? lower(config().getString("stats.storage")) : "disabled"));
        metrics.addCustomChart(new SimplePie("replays", () -> String.valueOf(config().getBoolean("replays.enabled"))));
        metrics.addCustomChart(new SimplePie("rewards", () -> String.valueOf(config().getBoolean("rewards.enabled"))));
        metrics.addCustomChart(new SimplePie("map_voting", () ->
                hasArena(GameType.SURVIVAL_GAMES) ? String.valueOf(config().getBoolean("survival-games.map-voting.enabled")) : null));
        metrics.addCustomChart(new SimplePie("arenas", () -> bucket(plugin.getArenaManager().getArenas().size())));
        metrics.addCustomChart(new SimplePie("kits", () -> bucket(plugin.getKits().getKits().size())));
        metrics.addCustomChart(new SimplePie("game_types", this::usedGameTypes));
        metrics.addCustomChart(new AdvancedPie("arena_types", this::arenaTypes));
        metrics.addCustomChart(new DrilldownPie("team_sizes", this::teamSizes));
        metrics.addCustomChart(new DrilldownPie("modules", this::modules));
        metrics.addCustomChart(new AdvancedPie("integrations", Telemetry::integrations));
        metrics.addCustomChart(new AdvancedPie("lobby_elements", this::lobbyElements));
        metrics.addCustomChart(new SimplePie("varo_play_time", () ->
                hasArena(GameType.VARO) ? Integer.toString(config().getInt("varo.play-time")) : null));
        metrics.addCustomChart(new AdvancedPie("varo_rules", this::varoRules));
        // what is going on right now
        metrics.addCustomChart(new SingleLineChart("players_survival_games", () -> players(GameType.SURVIVAL_GAMES)));
        metrics.addCustomChart(new SingleLineChart("players_varo", () -> players(GameType.VARO)));
        metrics.addCustomChart(new SingleLineChart("varo_running", () -> varoDays().values().stream().mapToInt(i -> i).sum()));
        metrics.addCustomChart(new AdvancedPie("varo_days", this::varoDays));
        // what happened since the last submission
        metrics.addCustomChart(new SingleLineChart("games_played", () -> games.getAndSet(0)));
        metrics.addCustomChart(new AdvancedPie("game_types_played", () -> drain(gameTypes)));
        metrics.addCustomChart(new AdvancedPie("players_per_game", () -> drain(gameSizes)));
        metrics.addCustomChart(new AdvancedPie("survival_games_duration", () -> drain(durations)));
        metrics.addCustomChart(new AdvancedPie("survival_games_endings", () -> drain(endings)));
        metrics.addCustomChart(new AdvancedPie("varo_length", () -> drain(varoLengths)));
        metrics.addCustomChart(new AdvancedPie("feature_usage", () -> drain(features)));
        metrics.addCustomChart(new AdvancedPie("join_methods", () -> drain(joins)));
    }

    // ---------------------------------------------------------------- events

    /**
     * the game ends with a winner or a draw (it is still running)
     */
    public void gamePlayed(Game game, boolean draw) {
        games.incrementAndGet();
        count(gameTypes, game.getType().getKey());
        count(gameSizes, playerBucket(game.getParticipants().size()));
        if (game instanceof SurvivalGame survivalGame) {
            count(durations, minuteBucket(survivalGame.getSeconds() / 60));
            count(endings, draw ? "draw" : survivalGame.isDeathmatch() ? "in the deathmatch" : "before the deathmatch");
        } else if (game instanceof VaroGame varo) {
            count(varoLengths, dayBucket(varo.getDay()));
        }
    }

    public void used(Feature feature) {
        count(features, feature.label);
    }

    public void joined(Join join) {
        count(joins, join.label);
    }

    private static void count(Map<String, AtomicInteger> counters, String key) {
        counters.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }

    // ---------------------------------------------------------------- charts

    private ConfigFile config() {
        return plugin.getFileManager().getConfigFile();
    }

    private boolean hasArena(GameType type) {
        return plugin.getArenaManager().getArenas().stream().anyMatch(arena -> arena.getType() == type);
    }

    /**
     * @return the game types the server has arenas for: one of them, both or none
     */
    String usedGameTypes() {
        boolean survivalGames = hasArena(GameType.SURVIVAL_GAMES), varo = hasArena(GameType.VARO);
        if (survivalGames && varo) return "both";
        return survivalGames ? GameType.SURVIVAL_GAMES.getKey() : varo ? GameType.VARO.getKey() : "none";
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
     * @return the number of arenas per game type and team size
     */
    Map<String, Map<String, Integer>> teamSizes() {
        Map<String, Map<String, Integer>> values = new HashMap<>();
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            values.computeIfAbsent(arena.getType().getKey(), key -> new HashMap<>())
                    .merge(Integer.toString(arena.getTeamSize()), 1, Integer::sum);
        }
        return values;
    }

    /**
     * @return the game types every module is used in (modules.&lt;module&gt; in config.yml): one of them, both or
     * disabled
     */
    Map<String, Map<String, Integer>> modules() {
        Map<String, Map<String, Integer>> values = new HashMap<>();
        ConfigurationSection section = config().getSection("modules");
        if (section == null) return values;
        for (String module : section.getKeys(false)) {
            List<String> types = GameType.keys().stream()
                    .filter(type -> section.getStringList(module + ".games").stream().anyMatch(type::equalsIgnoreCase))
                    .toList();
            String use = !section.getBoolean(module + ".enabled", true) || types.isEmpty() ? "disabled"
                    : types.size() > 1 ? "both" : types.getFirst();
            values.put(module, Map.of(use, 1));
        }
        return values;
    }

    /**
     * @return the optional plugins on the server
     */
    static Map<String, Integer> integrations() {
        Map<String, Integer> values = new HashMap<>();
        for (String name : INTEGRATIONS) {
            if (Bukkit.getPluginManager().isPluginEnabled(name)) values.put(name, 1);
        }
        return values;
    }

    /**
     * @return the number of join signs, join NPCs and leaderboards
     */
    Map<String, Integer> lobbyElements() {
        Map<String, Integer> values = new HashMap<>();
        put(values, "join signs", plugin.getSignManager().getSigns().size());
        put(values, "join npcs", plugin.getNpcSupport().getLinks().size());
        put(values, "leaderboard holograms", plugin.getLeaderboards().getHolograms().size());
        put(values, "leaderboard signs", plugin.getLeaderboards().getSigns().size());
        return values;
    }

    /**
     * @return the optional rules of a Varo that are turned on (only servers with a Varo arena)
     */
    Map<String, Integer> varoRules() {
        Map<String, Integer> values = new HashMap<>();
        if (!hasArena(GameType.VARO)) return values;
        if (!config().getData().getStringList("varo.play-hours").isEmpty()) values.put("play hours", 1);
        if (config().getInt("varo.peaceful-days") > 0) values.put("peaceful days", 1);
        if (config().getInt("varo.inactive-days") > 0) values.put("inactive days", 1);
        if (config().getInt("varo.coordinates-from-day") > 0) values.put("daily coordinates", 1);
        if (!config().getData().getStringList("varo.strikes").isEmpty()) values.put("strikes", 1);
        if (config().getInt("varo.backups") > 0) values.put("backups", 1);
        if (config().getInt("varo.offline-npc-seconds") > 0) values.put("offline npc", 1);
        if (config().getInt("varo.combat-radius") > 0) values.put("combat radius", 1);
        if (config().getInt("varo.join-protection") > 0) values.put("join protection", 1);
        if (config().getInt("varo.world-border.size") > 0) values.put("world border", 1);
        return values;
    }

    /**
     * @return the players (without spectators) in the games of the type
     */
    int players(GameType type) {
        return plugin.getGameManager().getGames().stream().filter(game -> game.getType() == type)
                .mapToInt(game -> game.getPlayers().size()).sum();
    }

    /**
     * @return the running Varos by the day they are on
     */
    Map<String, Integer> varoDays() {
        Map<String, Integer> values = new HashMap<>();
        for (Game game : plugin.getGameManager().getGames()) {
            if (game instanceof VaroGame varo && game.getState() == GameState.RUNNING) {
                values.merge(dayBucket(varo.getDay()), 1, Integer::sum);
            }
        }
        return values;
    }

    private static void put(Map<String, Integer> values, String key, int amount) {
        if (amount > 0) values.put(key, amount);
    }

    /**
     * @return the counts since the last submission, the counters start again at 0
     */
    Map<String, Integer> drain(Chart chart) {
        return drain(switch (chart) {
            case GAME_TYPES -> gameTypes;
            case GAME_SIZES -> gameSizes;
            case DURATIONS -> durations;
            case ENDINGS -> endings;
            case VARO_LENGTHS -> varoLengths;
            case FEATURES -> features;
            case JOINS -> joins;
        });
    }

    /**
     * the charts with counters (for the tests)
     */
    enum Chart {
        GAME_TYPES, GAME_SIZES, DURATIONS, ENDINGS, VARO_LENGTHS, FEATURES, JOINS
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

    static String minuteBucket(int minutes) {
        if (minutes < 5) return "< 5 min";
        if (minutes < 10) return "5-10 min";
        if (minutes < 15) return "10-15 min";
        if (minutes < 20) return "15-20 min";
        if (minutes < 30) return "20-30 min";
        return "30+ min";
    }

    static String dayBucket(int days) {
        if (days <= 3) return "1-3 days";
        if (days <= 7) return "4-7 days";
        if (days <= 14) return "8-14 days";
        if (days <= 30) return "15-30 days";
        return "30+ days";
    }

    static String bucket(int amount) {
        if (amount <= 3) return Integer.toString(amount);
        if (amount <= 10) return "4-10";
        return "10+";
    }

    @Nullable
    private static String lower(@Nullable String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

}
