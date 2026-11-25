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
import io.sentry.SentryLevel;
import io.sentry.protocol.Feedback;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryId;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
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
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;
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
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
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
    /**
     * a lag report is sent at the latest after the server thread is blocked this long
     */
    static final long LAG_REPORT_AFTER = 10_000;
    private static final Pattern SG_COMMAND = Pattern.compile("(?is)/?(survivalgames:)?(sg|survivalgames|varo)(\\s+(\\S+).*)?");
    private final AtomicInteger lagReports = new AtomicInteger();
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
    private BukkitTask tickTask;
    @Nullable
    private Thread watchdog;
    /**
     * System.nanoTime() of the last server tick (0: the server isn't ticking yet)
     */
    private volatile long lastTick;

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
        boolean late;
        try {
            // plugins are enabled before the first tick when the server starts
            late = Bukkit.getCurrentTick() > 0;
        } catch (RuntimeException exception) {
            late = false;
        }
        this.lateLoad = late;
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
            scope.setTag("network_mode", lower(plugin.getNetworkManager().getMode().name()));
            scope.setTag("game_types", usedGameTypes());
            scope.setTag("late_load", String.valueOf(lateLoad));
            scope.setContexts("system", systemInfo());
        });
        breadcrumb("plugin", lateLoad ? "enabled while the server was running (/reload or a plugin manager)" : "enabled");
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
        startWatchdog();
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
        report(thrown, SentryLevel.ERROR, CURRENT_GAME.get());
    }

    private void report(Throwable thrown, SentryLevel level, @Nullable Game game) {
        Hint hint = settings();
        Map<String, Object> state = serverState();
        Map<String, Object> games = gamesContext();
        Map<String, Object> server = serverInfo();
        Map<String, Object> plugins = pluginsContext();
        Map<String, Object> gameContext = game == null ? null : gameContext(game);
        Sentry.captureException(thrown, hint, scope -> {
            scope.setLevel(level);
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
     * @return config.yml and loot.yml as attachments, unless sentry.include-config is false
     */
    private Hint settings() {
        Hint hint = new Hint();
        if (config().getBoolean("sentry.include-config")) {
            attach(hint, "config.yml", plugin.getFileManager().getConfigFile());
            attach(hint, "loot.yml", plugin.getFileManager().getLootFile());
        }
        return hint;
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
        return game instanceof SurvivalGame survivalGame && survivalGame.isDeathmatch()
                && game.getState() == GameState.RUNNING ? "deathmatch" : lower(game.getState().name());
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

    // ---------------------------------------------------------------- lag watchdog

    /**
     * a separate thread notices when the server thread is blocked (sentry.lag-threshold milliseconds) by the code of
     * the plugin, and reports where it hangs (lag spikes, freezes); 0 turns it off
     */
    private void startWatchdog() {
        long threshold = config().getInt("sentry.lag-threshold");
        if (threshold <= 0 || !Bukkit.isPrimaryThread()) return;
        Thread main = Thread.currentThread();
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> lastTick = System.nanoTime(), 1, 1);
        Thread thread = new Thread(() -> watch(main, threshold), "SurvivalGames Watchdog");
        thread.setDaemon(true);
        thread.start();
        this.watchdog = thread;
    }

    private void watch(Thread main, long threshold) {
        long stalledTick = 0;
        long blocked = 0;
        StackTraceElement[] sample = null;
        boolean sent = false;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                return;
            }
            long last = lastTick;
            if (last == 0 || Bukkit.isStopping()) continue;
            long millis = (System.nanoTime() - last) / 1_000_000;
            if (millis >= threshold) {
                if (stalledTick != last) {
                    // a new stall
                    stalledTick = last;
                    sample = null;
                    sent = false;
                }
                blocked = millis;
                // the first moment the plugin is in the stack (a freeze caused by others isn't reported)
                if (sample == null) {
                    StackTraceElement[] stack = main.getStackTrace();
                    if (hasOwnFrame(stack)) sample = stack;
                }
                if (sample != null && !sent && millis >= LAG_REPORT_AFTER) {
                    sent = true;
                    reportLag(sample, millis, true);
                }
            } else if (stalledTick != 0) {
                if (sample != null && !sent) reportLag(sample, blocked, false);
                stalledTick = 0;
                sample = null;
            }
        }
    }

    static boolean hasOwnFrame(StackTraceElement[] stack) {
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (className.startsWith(PACKAGE) && !className.startsWith(PACKAGE + "libs.")) return true;
        }
        return false;
    }

    private void reportLag(StackTraceElement[] stack, long millis, boolean stillBlocked) {
        ServerThreadBlocked lag = new ServerThreadBlocked(millis, stillBlocked, stack);
        String signature = signature(lag);
        synchronized (reported) {
            if (lagReports.get() >= MAX_LAG_REPORTS || !reported.add(signature)) return;
        }
        lagReports.incrementAndGet();
        try {
            report(lag, SentryLevel.WARNING, null);
        } catch (RuntimeException exception) {
            Debug.info("Couldn't report the lag: " + exception);
        }
    }

    /**
     * the server thread was blocked by the plugin, the stack trace shows where
     */
    static class ServerThreadBlocked extends RuntimeException {
        ServerThreadBlocked(long millis, boolean stillBlocked, StackTraceElement[] stack) {
            super("The server thread was blocked for " + millis + " ms" + (stillBlocked ? " (and still is)" : ""),
                    null, false, true);
            setStackTrace(stack);
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
        Sentry.feedback().capture(feedback, hint, scope -> {
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
        if (watchdog != null) {
            watchdog.interrupt();
            watchdog = null;
        }
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
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
        breadcrumb("feature", feature.label);
    }

    public void joined(Join join) {
        count(joins, join.label);
        breadcrumb("join", "a player joined a game (" + join.label + ")");
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
