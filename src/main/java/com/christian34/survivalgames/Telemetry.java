package com.christian34.survivalgames;

import com.christian34.survivalgames.files.ConfigFile;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.VaroGame;
import com.christian34.survivalgames.files.PluginFile;
import io.sentry.Attachment;
import io.sentry.Hint;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Feedback;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryId;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

/**
 * Anonymous error reports (Sentry, opt-out with sentry.enabled in config.yml): only exceptions with SurvivalGames in
 * their stack trace, no server name, no player names or uuids (replaced), no user data; the Sentry project must not
 * store IP addresses (project settings - Security &amp; Privacy). Unless sentry.include-config is false, the settings
 * of config.yml that differ from the defaults are attached (secrets, addresses and player names removed), so the error
 * can be reproduced. Lag reports come from the {@link LagWatchdog}, the statistics of bStats from {@link UsageStats}.
 *
 * @author Christian34
 */
public class Telemetry {
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
    private final SurvivalGames plugin;
    /**
     * the signatures of the errors reported since the server started
     */
    private final Set<String> reported = new HashSet<>();
    /**
     * the game whose tick is running on this thread (its error gets the game as context)
     */
    private static final ThreadLocal<Game> CURRENT_GAME = new ThreadLocal<>();
    /**
     * the last events of the plugin in an error report
     */
    static final int MAX_BREADCRUMBS = 30;
    /**
     * at most this many lag reports per server start (they are less important than errors)
     */
    static final int MAX_LAG_REPORTS = 5;
    private static final Pattern SG_COMMAND = Pattern.compile("(?is)/?(survivalgames:)?(sg|survivalgames|varo)(\\s+(\\S+).*)?");
    private final AtomicInteger lagReports = new AtomicInteger();
    @Nullable
    private YamlConfiguration defaults;
    /**
     * the plugin was loaded while the server was already running (/reload, PlugMan): a common cause of odd errors
     */
    private final boolean lateLoad;
    private final long enabledAt = System.currentTimeMillis();
    @Nullable
    private Handler handler;
    @Nullable
    private Listener commandListener;
    @Nullable
    private LagWatchdog watchdog;
    /**
     * the plugin is enabled completely
     */
    private volatile boolean started;

    public Telemetry(SurvivalGames plugin) {
        this.plugin = plugin;
        boolean late;
        try {
            // plugins are enabled before the first tick when the server starts
            late = Bukkit.getCurrentTick() > 0;
        } catch (RuntimeException exception) {
            late = false;
        }
        this.lateLoad = late;
    }

    /**
     * the plugin is enabled: statistics, and the tags that need the arenas and the network mode
     */
    public void start() {
        // -Dsg.offline=true: nothing is sent (tests)
        if (UpdateChecker.isOffline()) return;
        this.started = true;
        if (!Sentry.isEnabled()) return;
        Sentry.configureScope(scope -> {
            scope.setTag("network_mode", lower(plugin.getNetworkManager().getMode().name()));
        });
        breadcrumb("plugin", "started");
    }

    // ---------------------------------------------------------------- error reports

    /**
     * as early as possible (right after config.yml is loaded): errors while the arenas and games are loaded, or the
     * plugin can't be enabled at all, are reported too
     */
    public void startErrorReports() {
        if (UpdateChecker.isOffline() || SENTRY_DSN.isBlank() || !config().getBoolean("sentry.enabled")) return;
        Sentry.init(options -> {
            options.setDsn(SENTRY_DSN);
            options.setRelease("survivalgames@" + plugin.getPluginMeta().getVersion());
            options.setEnvironment("production");
            // no data about the server or its players (personal data is off by default: nothing of DataCollection
            // is enabled here)
            options.setAttachServerName(false);
            options.setServerName(null);
            // the last events of the plugin (games, eliminations, commands without arguments), without player names
            options.setMaxBreadcrumbs(MAX_BREADCRUMBS);
            options.setBeforeBreadcrumb((breadcrumb, hint) -> {
                if (breadcrumb.getMessage() != null) breadcrumb.setMessage(anonymize(breadcrumb.getMessage()));
                return breadcrumb;
            });
            options.addInAppInclude("com.christian34.survivalgames");
            options.addInAppExclude(PACKAGE + "libs");
            // only what this plugin reports, not the errors of other plugins
            options.setEnableUncaughtExceptionHandler(false);
            options.setEnableShutdownHook(false);
            options.setEnableExternalConfiguration(false);
            options.setSendClientReports(false);
            options.setBeforeSend((event, hint) -> minimize(event));
            // -Dsg.sentry.debug=true: Sentry logs what it sends and what the server answers
            options.setDebug(Boolean.getBoolean("sg.sentry.debug"));
            // global: the tags and breadcrumbs of the main thread are in the reports of every thread (async tasks,
            // the watchdog); this copy of Sentry is relocated and only used by this plugin
        }, true);
        Sentry.configureScope(scope -> {
            scope.setTag("server", Bukkit.getName() + " " + Bukkit.getMinecraftVersion());
            scope.setTag("java", System.getProperty("java.version"));
            scope.setTag("late_load", String.valueOf(lateLoad));
            scope.setContexts("system", systemInfo());
        });
        breadcrumb("plugin", lateLoad ? "enabling while the server is running (/reload or a plugin manager)" : "enabling");
        this.commandListener = new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onCommand(PlayerCommandPreprocessEvent event) {
                commandUsed(event.getMessage());
            }

            @EventHandler(priority = EventPriority.MONITOR)
            public void onCommand(ServerCommandEvent event) {
                commandUsed(event.getCommand());
            }
        };
        Bukkit.getPluginManager().registerEvents(commandListener, plugin);
        this.watchdog = new LagWatchdog(plugin, this);
        watchdog.start(config().getInt("sentry.lag-threshold"));
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
     * sends the error with the changed settings of the server (config.yml) unless sentry.include-config is false
     */
    private void report(Throwable thrown) {
        report(thrown, SentryLevel.ERROR, CURRENT_GAME.get(), null);
    }

    /**
     * @param lag details of a blocked server thread (null: an error)
     */
    private void report(Throwable thrown, SentryLevel level, @Nullable Game game, @Nullable Map<String, Object> lag) {
        Hint hint = settings();
        String gameTypes = gameTypesNow();
        Map<String, Object> state = serverState();
        Map<String, Object> games = gamesContext();
        Map<String, Object> server = serverInfo();
        Map<String, Object> plugins = pluginsContext();
        Map<String, Object> gameContext = game == null ? null : gameContext(game);
        Sentry.captureException(thrown, hint, scope -> {
            scope.setLevel(level);
            // errors while the plugin is enabled (loading arenas, games) or after
            scope.setTag("plugin_phase", started ? "running" : "startup");
            // now, not when the server started (arenas are created later)
            scope.setTag("game_types", gameTypes);
            if (lag != null) scope.setContexts("lag", lag);
            // the numbers in the report, the groups as tags to filter by (e.g. only on full servers)
            scope.setContexts("server state", state);
            if (state.get("players_online") instanceof Integer players) scope.setTag("players", countBucket(players));
            if (state.get("running_games") instanceof Integer running) scope.setTag("running_games", countBucket(running));
            if (state.get("tps") instanceof Double tps) scope.setTag("tps", tpsBucket(tps));
            scope.setContexts("games", games);
            scope.setContexts("server", server);
            scope.setContexts("plugins", plugins);
            if (gameContext != null) {
                // the game whose tick failed
                scope.setContexts("game", gameContext);
                scope.setTag("game_type", game.getType().getKey());
                scope.setTag("game_state", String.valueOf(gameContext.get("phase")));
            }
        });
    }

    /**
     * @return the settings of config.yml that differ from the defaults as attachment, unless sentry.include-config is
     * false
     */
    private Hint settings() {
        Hint hint = new Hint();
        if (config().getBoolean("sentry.include-config")) attach(hint, "config.yml", config());
        return hint;
    }

    /**
     * @return the default config.yml of the plugin (null if the jar hasn't got it)
     */
    @Nullable
    private YamlConfiguration defaults() {
        if (defaults == null) {
            try (InputStream in = plugin.getResource("config.yml")) {
                if (in != null) {
                    this.defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            } catch (IOException | RuntimeException exception) {
                Debug.info("Couldn't read the default config.yml: " + exception);
            }
        }
        return defaults;
    }

    /**
     * @return the game types the server has arenas for, "unknown" while the arenas are loaded
     */
    private String gameTypesNow() {
        try {
            return plugin.getArenaManager() == null ? "unknown" : plugin.getUsageStats().usedGameTypes();
        } catch (RuntimeException exception) {
            return "unknown";
        }
    }

    /**
     * runs the action (logging an error) with the game as context of the error report
     */
    public static void inGame(Game game, Runnable action) {
        Game previous = CURRENT_GAME.get();
        CURRENT_GAME.set(game);
        try {
            action.run();
        } finally {
            if (previous == null) CURRENT_GAME.remove();
            else CURRENT_GAME.set(previous);
        }
    }

    @Nullable
    static Game currentGame() {
        return CURRENT_GAME.get();
    }

    /**
     * @return the game in detail: type, phase, time, players, teams (no player names)
     */
    static Map<String, Object> gameContext(Game game) {
        Map<String, Object> context = new LinkedHashMap<>();
        put(context, "type", () -> game.getType().getKey());
        put(context, "arena", () -> game.getArena().getName());
        put(context, "phase", () -> phase(game));
        put(context, "countdown", game::getCountdown);
        put(context, "running_seconds", game::getRunningSeconds);
        if (game instanceof VaroGame varo) put(context, "varo_day", varo::getDay);
        put(context, "participants", () -> game.getParticipants().size());
        put(context, "alive", () -> game.getAlive().size());
        put(context, "players", () -> game.getPlayers().size());
        put(context, "spectators", () -> game.getSpectators().size());
        put(context, "teams", () -> game.getTeams().getTeams().size());
        put(context, "alive_teams", () -> game.getAliveTeams().size());
        put(context, "team_size", () -> game.getArena().getTeamSize());
        put(context, "spawns", () -> game.getArena().getSpawnCount());
        return context;
    }

    /**
     * @return every game in one line (at most 20)
     */
    Map<String, Object> gamesContext() {
        Map<String, Object> context = new TreeMap<>();
        put(context, "count", () -> plugin.getGameManager().getGames().size());
        try {
            plugin.getGameManager().getGames().stream().limit(20).forEach(game -> {
                Map<String, Object> details = gameContext(game);
                context.put(details.get("type") + " " + details.get("arena"), details.get("phase") + ", "
                        + details.get("running_seconds") + " s"
                        + (details.containsKey("varo_day") ? ", day " + details.get("varo_day") : "")
                        + ", " + details.get("alive") + "/" + details.get("participants") + " alive, "
                        + details.get("spectators") + " spectators, team size " + details.get("team_size"));
            });
        } catch (RuntimeException exception) {
            // a game list changed by another thread
        }
        return context;
    }

    private static String phase(Game game) {
        return game.isFinalPhase() && game.getState() == GameState.RUNNING ? "deathmatch" : lower(game.getState().name());
    }

    /**
     * @return the other plugins and their versions (conflicts, outdated dependencies)
     */
    static Map<String, Object> pluginsContext() {
        Map<String, Object> context = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        try {
            for (Plugin other : Bukkit.getPluginManager().getPlugins()) {
                String version = other.getPluginMeta().getVersion();
                context.put(other.getName(), other.isEnabled() ? version : version + " (disabled)");
            }
        } catch (RuntimeException exception) {
            // not every server implements the plugin meta
        }
        return context;
    }

    /**
     * @return the exact build of the server, its settings that matter for errors and how long it has been running
     */
    Map<String, Object> serverInfo() {
        Map<String, Object> context = new LinkedHashMap<>();
        put(context, "version", Bukkit::getVersion);
        put(context, "bukkit_version", Bukkit::getBukkitVersion);
        put(context, "late_load", () -> lateLoad);
        put(context, "uptime_minutes", () -> ManagementFactory.getRuntimeMXBean().getUptime() / 60_000);
        put(context, "plugin_uptime_minutes", () -> (System.currentTimeMillis() - enabledAt) / 60_000);
        put(context, "online_mode", Bukkit::getOnlineMode);
        put(context, "max_players", Bukkit::getMaxPlayers);
        put(context, "view_distance", Bukkit::getViewDistance);
        put(context, "simulation_distance", Bukkit::getSimulationDistance);
        put(context, "language", () -> config().getString("language"));
        put(context, "stats_storage", () -> config().getBoolean("stats.enabled")
                ? lower(config().getString("stats.storage")) : "disabled");
        return context;
    }

    /**
     * @return the machine: operating system, CPU cores, Java
     */
    static Map<String, Object> systemInfo() {
        Map<String, Object> context = new LinkedHashMap<>();
        put(context, "os", () -> System.getProperty("os.name") + " " + System.getProperty("os.version"));
        put(context, "arch", () -> System.getProperty("os.arch"));
        put(context, "cpu_cores", () -> Runtime.getRuntime().availableProcessors());
        // the version is in the tag java (Sentry would remove "25.0.5.1" here as an IP address)
        put(context, "java_vendor", () -> System.getProperty("java.vendor"));
        put(context, "java_vm", () -> System.getProperty("java.vm.name"));
        put(context, "memory_max_mb", () -> Runtime.getRuntime().maxMemory() / (1024 * 1024));
        return context;
    }

    // ---------------------------------------------------------------- breadcrumbs

    /**
     * remembers an event for the next error report (nothing if error reports are disabled)
     *
     * @param category e.g. game, command, feature
     */
    public void breadcrumb(String category, String message) {
        if (!Sentry.isEnabled()) return;
        try {
            Sentry.addBreadcrumb(message, category);
        } catch (RuntimeException exception) {
            // never more important than what the plugin does
        }
    }

    /**
     * a game changed its state
     */
    public void gameState(Game game, GameState state) {
        breadcrumb("game", game.getType().getKey() + " '" + game.getArena().getName() + "': " + lower(state.name()));
    }

    /**
     * a participant is out
     */
    public void eliminated(Game game) {
        breadcrumb("game", game.getType().getKey() + " '" + game.getArena().getName() + "': a player is out, "
                + game.getAlive().size() + " alive");
    }

    /**
     * a command of the plugin: only its first word (e.g. "sg join"), never the arguments
     */
    void commandUsed(String line) {
        String command = sgCommand(line);
        if (command != null) breadcrumb("command", command);
    }

    @Nullable
    static String sgCommand(String line) {
        var matcher = SG_COMMAND.matcher(line.trim());
        if (!matcher.matches()) return null;
        String sub = matcher.group(4);
        return sub == null ? "sg" : "sg " + sub.toLowerCase(Locale.ROOT);
    }

    /**
     * the server thread was blocked: reported if the plugin caused it
     */
    void reportLag(List<StackTraceElement[]> samples, long millis, long gcMillis, boolean stillBlocked) {
        LagWatchdog.LagCulprit culprit = LagWatchdog.culprit(samples);
        // the JVM mostly collected garbage (too little memory): not caused by the plugin
        if (culprit == null || gcMillis * 2 >= millis || LagWatchdog.isClassLoading(culprit.stack())) return;
        LagWatchdog.ServerThreadBlocked lag = new LagWatchdog.ServerThreadBlocked(millis, stillBlocked, culprit.stack());
        String signature = signature(lag);
        synchronized (reported) {
            if (lagReports.get() >= MAX_LAG_REPORTS || !reported.add(signature)) return;
        }
        lagReports.incrementAndGet();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("blocked_ms", millis);
        details.put("still_blocked", stillBlocked);
        details.put("gc_ms", gcMillis);
        details.put("samples", culprit.samples());
        details.put("samples_in_plugin", culprit.inPlugin());
        details.put("samples_at_line", culprit.atFrame());
        details.put("line", culprit.frame());
        try {
            report(lag, SentryLevel.WARNING, null, details);
        } catch (RuntimeException exception) {
            Debug.info("Couldn't report the lag: " + exception);
        }
    }

    // ---------------------------------------------------------------- feedback

    /**
     * /sg report: a description of a problem from an admin, linked to the last error report
     *
     * @return false if error reports are disabled
     */
    public boolean sendFeedback(String text) {
        if (!Sentry.isEnabled()) return false;
        Feedback feedback = new Feedback(anonymize(text));
        SentryId lastError = Sentry.getLastEventId();
        if (lastError != null && !SentryId.EMPTY_ID.equals(lastError)) feedback.setAssociatedEventId(lastError);
        Hint hint = settings();
        Map<String, Object> state = serverState();
        Map<String, Object> games = gamesContext();
        Map<String, Object> server = serverInfo();
        Map<String, Object> plugins = pluginsContext();
        String gameTypes = gameTypesNow();
        Sentry.feedback().capture(feedback, hint, scope -> {
            scope.setTag("game_types", gameTypes);
            scope.setContexts("server state", state);
            scope.setContexts("games", games);
            scope.setContexts("server", server);
            scope.setContexts("plugins", plugins);
        });
        Sentry.flush(5000);
        return true;
    }

    /**
     * @return the load of the server when the error happened: players, games, TPS/MSPT, memory, worlds (numbers only,
     * every value on its own, so one that can't be read doesn't cost the others)
     */
    Map<String, Object> serverState() {
        Map<String, Object> state = new LinkedHashMap<>();
        put(state, "players_online", () -> Bukkit.getOnlinePlayers().size());
        put(state, "players_in_games", () -> plugin.getGameManager().getGames().stream()
                .mapToInt(game -> game.getPlayers().size()).sum());
        put(state, "spectators", () -> plugin.getGameManager().getGames().stream()
                .mapToInt(game -> game.getSpectators().size()).sum());
        put(state, "running_games", () -> (int) plugin.getGameManager().getGames().stream()
                .filter(game -> game.getState() == GameState.RUNNING).count());
        put(state, "games", () -> {
            // e.g. "survival-games running": 2, "survival-games deathmatch": 1, "varo lobby": 1
            Map<String, Integer> games = new TreeMap<>();
            for (Game game : plugin.getGameManager().getGames()) {
                games.merge(game.getType().getKey() + " " + phase(game), 1, Integer::sum);
            }
            return games;
        });
        put(state, "tps", () -> Math.round(Bukkit.getTPS()[0] * 10) / 10.0);
        put(state, "mspt", () -> Math.round(Bukkit.getAverageTickTime() * 10) / 10.0);
        Runtime runtime = Runtime.getRuntime();
        put(state, "memory_used_mb", () -> (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        put(state, "memory_max_mb", () -> runtime.maxMemory() / (1024 * 1024));
        put(state, "worlds", () -> Bukkit.getWorlds().size());
        put(state, "loaded_chunks", () -> Bukkit.getWorlds().stream().mapToInt(World::getChunkCount).sum());
        put(state, "entities", () -> Bukkit.getWorlds().stream().mapToInt(World::getEntityCount).sum());
        put(state, "plugins", () -> Bukkit.getPluginManager().getPlugins().length);
        return state;
    }

    private static void put(Map<String, Object> state, String key, Supplier<Object> value) {
        try {
            state.put(key, value.get());
        } catch (RuntimeException exception) {
            // e.g. a game list changed by another thread, or a server without the method
        }
    }

    static String countBucket(int amount) {
        if (amount <= 0) return "0";
        if (amount <= 5) return "1-5";
        if (amount <= 10) return "6-10";
        if (amount <= 25) return "11-25";
        if (amount <= 50) return "26-50";
        if (amount <= 100) return "51-100";
        return "100+";
    }

    static String tpsBucket(double tps) {
        if (tps >= 19.5) return "19.5+";
        if (tps >= 18) return "18-19.5";
        if (tps >= 15) return "15-18";
        if (tps >= 10) return "10-15";
        return "< 10";
    }

    private void attach(Hint hint, String name, PluginFile file) {
        try {
            byte[] content = redact(file.getData(), defaults()).getBytes(StandardCharsets.UTF_8);
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
        return redact(data, null);
    }

    /**
     * @param defaults null: all settings, otherwise only those that differ from the defaults (and those that aren't
     *                 in the defaults, e.g. the rules of the quells)
     */
    static String redact(ConfigurationSection data, @Nullable ConfigurationSection defaults) {
        YamlConfiguration copy = new YamlConfiguration();
        for (String key : data.getKeys(true)) {
            Object value = data.get(key);
            if (value instanceof ConfigurationSection) continue;
            if (defaults != null && !(defaults.get(key) instanceof ConfigurationSection)
                    && Objects.equals(value, defaults.get(key))) {
                continue;
            }
            copy.set(key, redact(key, value));
        }
        if (defaults != null) copy.options().setHeader(List.of("only the settings that differ from the defaults"));
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
     * @return true for the classes of the plugin (not its bundled libraries)
     */
    static boolean isOwnClass(String className) {
        return className.startsWith(PACKAGE) && !className.startsWith(PACKAGE + "libs.");
    }

    /**
     * @return true if SurvivalGames is in the stack trace (not its bundled libraries)
     */
    static boolean isOwnError(Throwable thrown) {
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            for (StackTraceElement element : cause.getStackTrace()) {
                if (isOwnClass(element.getClassName())) return true;
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
            if (isOwnClass(element.getClassName())) return thrown.getClass().getName() + "@" + element;
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
        if (watchdog != null) {
            watchdog.stop();
            watchdog = null;
        }
        if (commandListener != null) {
            HandlerList.unregisterAll(commandListener);
            commandListener = null;
        }
        if (handler != null) {
            Bukkit.getLogger().removeHandler(handler);
            plugin.getLogger().removeHandler(handler);
            handler = null;
        }
        if (Sentry.isEnabled()) Sentry.close();
    }

    private ConfigFile config() {
        return plugin.getFileManager().getConfigFile();
    }

    @Nullable
    private static String lower(@Nullable String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

}
