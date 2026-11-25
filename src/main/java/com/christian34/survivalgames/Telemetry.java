package com.christian34.survivalgames;

import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.files.ConfigFile;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.game.UhcGame;
import com.christian34.survivalgames.game.VaroGame;
import com.christian34.survivalgames.files.PluginFile;
import io.sentry.Attachment;
import io.sentry.Hint;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.protocol.SentryException;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

/**
 * Anonymous statistics (bStats, opt-out for all plugins in plugins/bStats/config.yml): the number of servers and
 * players, the server and Java version, a few settings of the plugin and how often its features are used. No names
 * (players, arenas, kits, teams) are sent.
 * <p>
 * Anonymous error reports (Sentry, opt-out with sentry.enabled in config.yml): only exceptions with SurvivalGames in
 * their stack trace, no server name, no player names or uuids (replaced), no user data; the Sentry project must not
 * store IP addresses (project settings - Security &amp; Privacy). Unless sentry.include-config is false, config.yml
 * and loot.yml are attached (secrets, addresses and player names removed), so the error can be reproduced.
 *
 * @author Christian34
 */
public class Telemetry {
    /**
     * the plugin on bstats.org, 0: nothing is sent
     */
    static final int BSTATS_ID = 34655;
    /**
     * the Sentry project (region US, Sentry is certified under the EU-U.S. Data Privacy Framework), empty: no error reports
     */
    static final String SENTRY_DSN = "https://94c990dbad599fc5faab85f2c906f94e@o393387.ingest.us.sentry.io/4512493446103040";
    /**
     * at most this many error reports per server start (a broken loop mustn't send thousands)
     */
    static final int MAX_REPORTS = 25;
    static final String PACKAGE = "com.christian34.survivalgames.";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    /**
     * settings whose values never leave the server (database login, webhooks, addresses of servers)
     */
    private static final Pattern SECRET_KEY = Pattern.compile(
            "(?i)(.+-)?(password|secret|token|webhook|url|address|host|user|username|ip)(-.+)?");
    /**
     * values that look like an address (jdbc:mysql://..., https://..., 1.2.3.4, localhost, play.example.com:25565)
     * whatever their key is
     */
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?is).*(://|\\b\\d{1,3}(\\.\\d{1,3}){3}\\b|\\blocalhost\\b|\\b[a-z0-9-]+(\\.[a-z0-9-]+)+:\\d{2,5}\\b).*");
    static final String REDACTED = "<redacted>";
    /**
     * the optional plugins (softdepend in plugin.yml)
     */
    private static final List<String> INTEGRATIONS = List.of("Vault", "PlaceholderAPI", "MiniPlaceholders", "Parties",
            "Spigot-Party-API-PAF", "Citizens", "FancyNpcs", "floodgate", "TAB", "Essentials", "Multiverse-Core",
            "Multiverse-Inventories");
    private final SurvivalGames plugin;
    /**
     * the finished games since the last bStats submission (every 30 minutes, the line chart adds them up)
     */
    private final AtomicInteger games = new AtomicInteger();
    /**
     * since the server started (a pie chart only shows the last submission, it would mostly be empty): the finished
     * games per game type, player count, length and ending, the features used and the ways the players joined
     */
    private final Map<String, AtomicInteger> gameTypes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> gameSizes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> durations = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> endings = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> varoLengths = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> uhcDurations = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> features = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> joins = new ConcurrentHashMap<>();
    /**
     * the signatures of the errors reported since the server started
     */
    private final Set<String> reported = new HashSet<>();
    @Nullable
    private Handler handler;

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
        REPLAY("replay watched"),
        BET("bet placed"),
        ALLIANCE("alliance formed"),
        FORCE_FIELD("force field bounce"),
        REAPING("reaping"),
        QUELL("quell"),
        ARENA_EVENT("gamemaker event");

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
        if (UpdateChecker.isOffline()) return;
        startMetrics();
        startErrorReports();
    }

    private void startMetrics() {
        if (BSTATS_ID <= 0) return;
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
        metrics.addCustomChart(new AdvancedPie("uhc_scenarios", this::uhcScenarios));
        // what is going on right now
        metrics.addCustomChart(new SingleLineChart("players_survival_games", () -> players(GameType.SURVIVAL_GAMES)));
        metrics.addCustomChart(new SingleLineChart("players_varo", () -> players(GameType.VARO)));
        metrics.addCustomChart(new SingleLineChart("players_uhc", () -> players(GameType.UHC)));
        metrics.addCustomChart(new SingleLineChart("players_duels", () -> players(GameType.DUELS)));
        metrics.addCustomChart(new SingleLineChart("varo_running", () -> varoDays().values().stream().mapToInt(i -> i).sum()));
        metrics.addCustomChart(new AdvancedPie("varo_days", this::varoDays));
        // what happened since the last submission
        metrics.addCustomChart(new SingleLineChart("games_played", () -> games.getAndSet(0)));
        // what happened since the server started
        metrics.addCustomChart(new AdvancedPie("game_types_played", () -> counts(gameTypes)));
        metrics.addCustomChart(new AdvancedPie("players_per_game", () -> counts(gameSizes)));
        metrics.addCustomChart(new AdvancedPie("survival_games_duration", () -> counts(durations)));
        metrics.addCustomChart(new AdvancedPie("survival_games_endings", () -> counts(endings)));
        metrics.addCustomChart(new AdvancedPie("varo_length", () -> counts(varoLengths)));
        metrics.addCustomChart(new AdvancedPie("uhc_duration", () -> counts(uhcDurations)));
        metrics.addCustomChart(new AdvancedPie("feature_usage", () -> counts(features)));
        metrics.addCustomChart(new AdvancedPie("join_methods", () -> counts(joins)));
    }

    // ---------------------------------------------------------------- error reports

    private void startErrorReports() {
        if (SENTRY_DSN.isBlank() || !config().getBoolean("sentry.enabled")) return;
        Sentry.init(options -> {
            options.setDsn(SENTRY_DSN);
            options.setRelease("survivalgames@" + plugin.getPluginMeta().getVersion());
            options.setEnvironment("production");
            // no data about the server or its players (personal data is off by default: nothing of DataCollection
            // is enabled here)
            options.setAttachServerName(false);
            options.setServerName(null);
            options.setMaxBreadcrumbs(0);
            // only what this plugin reports, not the errors of other plugins
            options.setEnableUncaughtExceptionHandler(false);
            options.setEnableShutdownHook(false);
            options.setEnableExternalConfiguration(false);
            options.setSendClientReports(false);
            options.setBeforeSend((event, hint) -> minimize(event));
            // -Dsg.sentry.debug=true: Sentry logs what it sends and what the server answers
            options.setDebug(Boolean.getBoolean("sg.sentry.debug"));
        });
        Sentry.configureScope(scope -> {
            scope.setTag("server", Bukkit.getName() + " " + Bukkit.getMinecraftVersion());
            scope.setTag("java", System.getProperty("java.version"));
            scope.setTag("network_mode", lower(plugin.getNetworkManager().getMode().name()));
            scope.setTag("game_types", usedGameTypes());
        });
        // errors of the plugin are logged by the server (event handlers, tasks) or the plugin itself (Debug)
        this.handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (shouldReport(record)) report(record.getThrown());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Bukkit.getLogger().addHandler(handler);
        // the same error from both loggers is only reported once (signature)
        plugin.getLogger().addHandler(handler);
        Debug.info("Anonymous error reports are sent to Sentry to find bugs (disable: sentry.enabled in config.yml)");
    }

    /**
     * @return true if the record is an error of the plugin that hasn't been reported yet (Debug.warn with an exception)
     */
    boolean shouldReport(LogRecord record) {
        Throwable thrown = record.getThrown();
        if (thrown == null || record.getLevel().intValue() < Level.WARNING.intValue() || !isOwnError(thrown)) return false;
        // every error once, and not too many
        String signature = signature(thrown);
        synchronized (reported) {
            return reported.size() < MAX_REPORTS && reported.add(signature);
        }
    }

    /**
     * sends the error with the settings of the server (config.yml, loot.yml) unless sentry.include-config is false
     */
    private void report(Throwable thrown) {
        Hint hint = new Hint();
        if (config().getBoolean("sentry.include-config")) {
            attach(hint, "config.yml", plugin.getFileManager().getConfigFile());
            attach(hint, "loot.yml", plugin.getFileManager().getLootFile());
        }
        Sentry.captureException(thrown, hint);
    }

    private static void attach(Hint hint, String name, @Nullable PluginFile file) {
        if (file == null) return;
        try {
            byte[] content = redact(file.getData()).getBytes(StandardCharsets.UTF_8);
            hint.addAttachment(new Attachment(content, name, "text/yaml"));
        } catch (RuntimeException exception) {
            // the report itself is more important than the settings
            Debug.info("Couldn't attach " + name + " to the error report: " + exception);
        }
    }

    /**
     * @return the settings as YAML without comments, without passwords, webhooks, addresses, uuids and the names of
     * online players
     */
    static String redact(ConfigurationSection data) {
        YamlConfiguration copy = new YamlConfiguration();
        for (String key : data.getKeys(true)) {
            Object value = data.get(key);
            if (value instanceof ConfigurationSection) continue;
            copy.set(key, redact(key, value));
        }
        return copy.saveToString();
    }

    private static Object redact(String key, Object value) {
        if (value instanceof String text) {
            if (text.isBlank()) return text;
            return SECRET_KEY.matcher(key.substring(key.lastIndexOf('.') + 1)).matches()
                    || SECRET_VALUE.matcher(text).matches() ? REDACTED : anonymize(text);
        }
        if (value instanceof List<?> list) {
            List<Object> values = new ArrayList<>();
            for (Object element : list) values.add(redact(key, element));
            return values;
        }
        // the entries of a list of sections (network.servers: name + address)
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> values = new LinkedHashMap<>();
            map.forEach((entryKey, entry) -> values.put(entryKey, redact(String.valueOf(entryKey), entry)));
            return values;
        }
        return value;
    }

    /**
     * @return true if SurvivalGames is in the stack trace (not its bundled libraries)
     */
    static boolean isOwnError(Throwable thrown) {
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            for (StackTraceElement element : cause.getStackTrace()) {
                String className = element.getClassName();
                if (className.startsWith(PACKAGE) && !className.startsWith(PACKAGE + "libs.")) return true;
            }
            if (cause.getCause() == cause) break;
        }
        return false;
    }

    /**
     * @return the type and the first line of the plugin in the stack trace
     */
    static String signature(Throwable thrown) {
        for (StackTraceElement element : thrown.getStackTrace()) {
            if (element.getClassName().startsWith(PACKAGE)) return thrown.getClass().getName() + "@" + element;
        }
        return thrown.getClass().getName();
    }

    /**
     * removes everything that could identify the server or its players
     */
    SentryEvent minimize(SentryEvent event) {
        event.setUser(null);
        event.setServerName(null);
        if (event.getExceptions() != null) {
            for (SentryException exception : event.getExceptions()) {
                if (exception.getValue() != null) exception.setValue(anonymize(exception.getValue()));
            }
        }
        if (event.getMessage() != null && event.getMessage().getFormatted() != null) {
            event.getMessage().setFormatted(anonymize(event.getMessage().getFormatted()));
        }
        return event;
    }

    /**
     * @return the text without uuids and names of online players
     */
    static String anonymize(String text) {
        String result = UUID_PATTERN.matcher(text).replaceAll("<uuid>");
        for (Player player : Bukkit.getOnlinePlayers()) {
            result = result.replace(player.getName(), "<player>");
        }
        return result;
    }

    /**
     * /sg debug sentry: checks that error reports arrive
     *
     * @return false if error reports are disabled
     */
    public boolean sendTestReport() {
        if (!Sentry.isEnabled()) return false;
        report(new IllegalStateException("Test report of /sg debug sentry"));
        Sentry.flush(5000);
        return true;
    }

    public void stop() {
        if (handler != null) {
            Bukkit.getLogger().removeHandler(handler);
            plugin.getLogger().removeHandler(handler);
            handler = null;
        }
        if (Sentry.isEnabled()) Sentry.close();
    }

    // ---------------------------------------------------------------- events

    /**
     * the game ends with a winner or a draw (it is still running)
     */
    public void gamePlayed(Game game, boolean draw) {
        games.incrementAndGet();
        count(gameTypes, game.getType().getKey());
        count(gameSizes, playerBucket(game.getParticipants().size()));
        if (game instanceof UhcGame uhc) {
            count(uhcDurations, minuteBucket(uhc.getSeconds() / 60));
        } else if (game.getType() == GameType.SURVIVAL_GAMES && game instanceof SurvivalGame survivalGame) {
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
     * @return the game types the server has arenas for (e.g. survival-games + uhc), none if there are no arenas
     */
    String usedGameTypes() {
        List<String> types = Arrays.stream(GameType.values()).filter(this::hasArena).map(GameType::getKey).toList();
        return types.isEmpty() ? "none" : String.join(" + ", types);
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
     * @return the game types every module is used in (modules.&lt;module&gt; in config.yml, e.g. survival-games +
     * varo) or disabled
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
                    : String.join(" + ", types);
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
     * @return the scenarios of UHC that are turned on (only servers with a UHC arena)
     */
    Map<String, Integer> uhcScenarios() {
        Map<String, Integer> values = new HashMap<>();
        if (!hasArena(GameType.UHC)) return values;
        for (String scenario : config().getData().getStringList("uhc.scenarios")) {
            values.put(scenario.trim().toLowerCase(Locale.ROOT), 1);
        }
        if (values.isEmpty()) values.put("none", 1);
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
     * @return the counts since the server started
     */
    Map<String, Integer> counts(Chart chart) {
        return counts(switch (chart) {
            case GAME_TYPES -> gameTypes;
            case GAME_SIZES -> gameSizes;
            case DURATIONS -> durations;
            case ENDINGS -> endings;
            case VARO_LENGTHS -> varoLengths;
            case UHC_DURATIONS -> uhcDurations;
            case FEATURES -> features;
            case JOINS -> joins;
        });
    }

    /**
     * the charts with counters (for the tests)
     */
    enum Chart {
        GAME_TYPES, GAME_SIZES, DURATIONS, ENDINGS, VARO_LENGTHS, UHC_DURATIONS, FEATURES, JOINS
    }

    static Map<String, Integer> counts(Map<String, AtomicInteger> counters) {
        Map<String, Integer> values = new HashMap<>();
        counters.forEach((key, count) -> values.put(key, count.get()));
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
