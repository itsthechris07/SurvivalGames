package com.christian34.survivalgames.stats;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.files.ConfigFile;
import com.christian34.survivalgames.files.FileManager;
import com.christian34.survivalgames.game.GameType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The statistics of the players (stats in config.yml): games, wins, kills and deaths per game type, kept in stats.yml
 * or a MySQL database (shared by the servers of a network). The storage is only used by an own thread, so a slow
 * database doesn't hold up the server. The statistics and the achievements of the online players and the best players
 * are cached for the placeholders.
 *
 * @author Christian34
 */
public class StatsManager implements Listener {
    /**
     * the number of the best players in /sg top and the placeholders %sg_top_...%
     */
    public static final int TOP_SIZE = 10;
    private final SurvivalGames plugin;
    /**
     * the statistics of the online players by game type
     */
    private final Map<UUID, Map<String, PlayerStats>> cache = new ConcurrentHashMap<>();
    /**
     * the best players of all game types
     */
    /**
     * the achievements of the online players (key - when they got it)
     */
    private final Map<UUID, Map<String, Long>> achievements = new ConcurrentHashMap<>();
    private volatile Map<Stat, List<PlayerStats>> top = Map.of();
    @Nullable
    private StatsStorage storage;
    @Nullable
    private ExecutorService service;
    @Nullable
    private Executor testExecutor;

    public StatsManager(SurvivalGames plugin) {
        this.plugin = plugin;
        start();
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshTop, 20L * 60, 20L * 60);
    }

    private ConfigFile config() {
        return plugin.getFileManager().getConfigFile();
    }

    /**
     * opens the storage of config.yml (nothing if the statistics are turned off)
     */
    public void start() {
        if (!config().getBoolean("stats.enabled")) return;
        if ("mysql".equalsIgnoreCase(config().getString("stats.storage"))) {
            this.storage = new SqlStorage(String.valueOf(config().getString("stats.mysql.url")),
                    String.valueOf(config().getString("stats.mysql.user")),
                    String.valueOf(config().getString("stats.mysql.password")),
                    String.valueOf(config().getString("stats.mysql.table")));
        } else {
            this.storage = new FileStorage(new File(FileManager.getPluginFolder(), "stats.yml"));
        }
        this.service = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "SurvivalGames statistics");
            thread.setDaemon(true);
            return thread;
        });
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadCache(player.getUniqueId());
        }
        refreshTop();
    }

    /**
     * waits for the statistics that are still being saved and closes the storage
     */
    public void stop() {
        ExecutorService service = this.service;
        StatsStorage storage = this.storage;
        this.service = null;
        this.storage = null;
        cache.clear();
        achievements.clear();
        this.top = Map.of();
        if (service != null) {
            service.shutdown();
            try {
                if (!service.awaitTermination(10, TimeUnit.SECONDS)) Debug.warn("Not all statistics have been saved");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        if (storage != null) storage.close();
    }

    /**
     * config.yml has been reloaded
     */
    public void reload() {
        stop();
        start();
    }

    /**
     * runs the storage on the given executor instead of the own thread (the tests use the main thread)
     */
    public void setExecutor(@Nullable Executor executor) {
        this.testExecutor = executor;
    }

    private Executor executor() {
        return testExecutor != null ? testExecutor : service == null ? Runnable::run : service;
    }

    public boolean isEnabled() {
        return storage != null;
    }

    /**
     * runs the task with the storage on its thread (nothing if the statistics are turned off)
     */
    private void run(Consumer<StatsStorage> task) {
        StatsStorage storage = this.storage;
        if (storage == null) return;
        try {
            executor().execute(() -> {
                try {
                    task.accept(storage);
                } catch (RuntimeException ex) {
                    Debug.warn("Couldn't save the statistics", ex);
                }
            });
        } catch (RuntimeException ex) {
            Debug.warn("Couldn't save the statistics", ex);
        }
    }

    /**
     * @return the result of the query on the thread of the storage (failed if the statistics are turned off)
     */
    private <T> CompletableFuture<T> query(Function<StatsStorage, T> query) {
        StatsStorage storage = this.storage;
        if (storage == null) return CompletableFuture.failedFuture(new IllegalStateException("statistics are off"));
        try {
            return CompletableFuture.supplyAsync(() -> query.apply(storage), executor());
        } catch (RuntimeException ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * runs the action with the result on the main thread
     *
     * @param failed runs if the result couldn't be loaded
     */
    public <T> void then(CompletableFuture<T> future, Consumer<T> action, Runnable failed) {
        future.whenComplete((result, error) -> {
            if (error != null) Debug.warn("Couldn't load the statistics", error);
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error == null) {
                    action.accept(result);
                } else {
                    failed.run();
                }
            });
        });
    }

    // ---------------------------------------------------------------- statistics

    /**
     * adds one to the statistic of the player in the game type
     */
    public void add(UUID uuid, String name, GameType type, Stat stat) {
        add(uuid, name, type, stat, 1);
    }

    /**
     * @param amount may be negative (rating)
     */
    public void add(UUID uuid, String name, GameType type, Stat stat, int amount) {
        if (amount == 0) return;
        run(storage -> {
            storage.add(uuid, name, type.getKey(), stat, amount);
            cache.computeIfPresent(uuid, (key, stats) -> {
                Map<String, PlayerStats> copy = new HashMap<>(stats);
                copy.put(type.getKey(), copy.getOrDefault(type.getKey(), PlayerStats.empty(uuid, name)).plus(stat, amount, name));
                return Map.copyOf(copy);
            });
            checkAchievements(uuid, name);
        });
    }

    /**
     * @return the Elo rating of an online player in the game type ({@link PlayerStats#RATING_START} if it isn't loaded)
     */
    public int getRating(UUID uuid, GameType type) {
        Map<String, PlayerStats> stats = cache.get(uuid);
        PlayerStats typeStats = stats == null ? null : stats.get(type.getKey());
        return typeStats == null ? PlayerStats.RATING_START : typeStats.get(Stat.RATING);
    }

    /**
     * @return true if the game type changes the rating of the players (stats.rating)
     */
    public boolean isRated(GameType type) {
        return isEnabled() && config().getBoolean("stats.rating.enabled")
                && config().getData().getStringList("stats.rating.games").stream()
                .anyMatch(key -> key.equalsIgnoreCase(type.getKey()));
    }

    /**
     * @return the statistics of the player by game type (empty if he has none)
     */
    public CompletableFuture<Map<String, PlayerStats>> load(UUID uuid) {
        return query(storage -> storage.load(uuid));
    }

    /**
     * @return the statistics of the player by game type (empty if there is no player with statistics and this name)
     */
    public CompletableFuture<Map<String, PlayerStats>> load(String name) {
        Player online = Bukkit.getPlayerExact(name);
        UUID uuid = online == null ? null : online.getUniqueId();
        return query(storage -> {
            UUID player = uuid != null ? uuid : storage.find(name);
            return player == null ? Map.of() : storage.load(player);
        });
    }

    /**
     * @param type null: all game types
     * @return the best players (at most {@link #TOP_SIZE})
     */
    public CompletableFuture<List<PlayerStats>> top(Stat stat, @Nullable GameType type) {
        return query(storage -> storage.top(stat, type == null ? null : type.getKey(), TOP_SIZE));
    }

    /**
     * @return the statistics of an online player by game type, null if they aren't loaded (yet)
     */
    @Nullable
    public Map<String, PlayerStats> getCached(UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * @return the best players of all game types (updated every minute)
     */
    public List<PlayerStats> getTop(Stat stat) {
        return top.getOrDefault(stat, List.of());
    }

    /**
     * loads the best players again (every minute)
     */
    public void refreshTop() {
        run(storage -> {
            Map<Stat, List<PlayerStats>> top = new EnumMap<>(Stat.class);
            for (Stat stat : Stat.values()) {
                top.put(stat, storage.top(stat, null, TOP_SIZE));
            }
            this.top = top;
        });
    }

    private void loadCache(UUID uuid) {
        run(storage -> {
            Map<String, PlayerStats> stats = Map.copyOf(storage.load(uuid));
            achievements.put(uuid, Map.copyOf(storage.loadAchievements(uuid)));
            cache.put(uuid, stats);
            // what he reached while he was away (the win of his team in a Varo) or before there were achievements
            if (!stats.isEmpty()) checkAchievements(uuid, stats.values().iterator().next().name());
        });
    }

    // ---------------------------------------------------------------- achievements

    /**
     * the achievements for the statistics of an online player (on the thread of the storage)
     */
    private void checkAchievements(UUID uuid, String name) {
        Map<String, PlayerStats> stats = cache.get(uuid);
        Achievements manager = plugin.getAchievements();
        if (stats == null || manager == null) return;
        manager.onStats(PlayerStats.total(uuid, name, stats.values()));
    }

    /**
     *  the achievements of an online player (key - when he got it), null if they aren't loaded (yet)
     */
    @Nullable
    public Map<String, Long> getCachedAchievements(UUID uuid) {
        return achievements.get(uuid);
    }

    /**
     * @return the achievements of the player (key - when he got it), empty if there is no player with statistics and
     * this name
     */
    public CompletableFuture<Map<String, Long>> loadAchievements(String name) {
        Player online = Bukkit.getPlayerExact(name);
        UUID uuid = online == null ? null : online.getUniqueId();
        return query(storage -> {
            UUID player = uuid != null ? uuid : storage.find(name);
            return player == null ? Map.of() : storage.loadAchievements(player);
        });
    }

    /**
     * the player gets the achievement
     *
     * @param unlocked runs on the main thread if he didn't have it yet
     */
    public void unlock(UUID uuid, String name, String achievement, Runnable unlocked) {
        Map<String, Long> cached = achievements.get(uuid);
        if (cached != null && cached.containsKey(achievement)) return;
        long time = System.currentTimeMillis();
        run(storage -> {
            if (!storage.addAchievement(uuid, name, achievement, time)) return;
            achievements.computeIfPresent(uuid, (key, old) -> {
                Map<String, Long> copy = new HashMap<>(old);
                copy.put(achievement, time);
                return Map.copyOf(copy);
            });
            if (Bukkit.isPrimaryThread()) {
                unlocked.run();
            } else if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, unlocked);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        loadCache(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        // after the statistics of his game have been saved
        run(storage -> {
            cache.remove(uuid);
            achievements.remove(uuid);
        });
    }

}
