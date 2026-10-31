package com.christian34.survivalgames.replay;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.Telemetry;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.arena.GameWorld;
import com.christian34.survivalgames.files.FileManager;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.VaroGame;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.user.GamePlayer;
import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The replays: the games are recorded while they run (a round of Survival Games as one recording, a Varo day by day)
 * and saved in plugins/SurvivalGames/replays. A recording is played in its own world - a fresh copy of the map, for a
 * Varo the backup of the world from the beginning of that day.
 *
 * @author Christian34
 */
public final class Replays implements Listener {
    public static final String PERMISSION = "sg.replay";
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,80}");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String VARO_DAY = "-day-";
    /**
     * the winners celebrate for 3 more seconds
     */
    private static final int END_TICKS = 60;
    /**
     * shorter recordings aren't kept
     */
    private static final int MIN_TICKS = 100;
    /**
     * a Varo runs all day: its recording is saved every 5 minutes
     */
    private static final int SAVE_INTERVAL = 20 * 60 * 5;
    private static final BlockFace[] NEIGHBORS = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.EAST,
            BlockFace.SOUTH, BlockFace.WEST};
    private final SurvivalGames plugin;
    private final Map<Game, Recorder> recorders = new LinkedHashMap<>();
    /**
     * how long the games that are over have been recorded since then
     */
    private final Map<Game, Integer> ending = new HashMap<>();
    /**
     * the recordings of the Varos that were stopped today: they go on when the game is back
     */
    private final Map<String, Recording> suspended = new HashMap<>();
    private final Map<String, Playback> playbacks = new LinkedHashMap<>();
    private final Map<UUID, Playback> viewers = new HashMap<>();
    /**
     * the recordings that are read right now and who waits for them
     */
    private final Map<String, List<UUID>> loading = new HashMap<>();
    private final Map<String, Cached> infos = new ConcurrentHashMap<>();
    /**
     * one file at a time (a recording is written while its list entry is read)
     */
    private final Object fileLock = new Object();
    private final BukkitTask task;
    /**
     * reads and writes the files
     */
    private Executor executor;
    private int age;
    private int worlds;

    /**
     * @param info null: the file isn't a recording
     */
    private record Cached(long modified, @Nullable Recording.Info info) {
    }

    public Replays(SurvivalGames plugin) {
        this.plugin = plugin;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        this.executor = work -> Bukkit.getScheduler().runTaskAsynchronously(plugin, work);
    }

    /**
     * reads and writes the files on the given executor instead of the threads of the server (the tests use the main
     * thread)
     */
    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    // ---------------------------------------------------------------- settings

    private ConfigurationSection config() {
        return plugin.getFileManager().getConfigFile().getData();
    }

    public boolean isEnabled() {
        return config().getBoolean("replays.enabled", true);
    }

    /**
     * @return true if the games of this type are recorded
     */
    private boolean records(Game game) {
        return isEnabled() && config().getStringList("replays.games").stream()
                .anyMatch(type -> type.equalsIgnoreCase(game.getType().getKey()));
    }

    public static File getFolder() {
        return new File(FileManager.getPluginFolder(), "replays");
    }

    private static File getFile(String id) {
        return new File(getFolder(), id + Recording.EXTENSION);
    }

    private static String key(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- recording

    private void tick() {
        age++;
        try {
            record();
        } catch (RuntimeException ex) {
            Debug.warn("Couldn't record the games", ex);
        }
        for (Playback playback : new ArrayList<>(playbacks.values())) {
            try {
                playback.tick();
            } catch (RuntimeException ex) {
                Debug.warn("The replay " + playback.getId() + " has been closed", ex);
                close(playback);
            }
        }
    }

    private void record() {
        for (Game game : plugin.getGameManager().getGames()) {
            Recorder recorder = recorders.get(game);
            if (recorder == null) {
                // also a Varo that was loaded when the server started
                if (game.getState() != GameState.RUNNING || game.isStopped() || !records(game)) continue;
                recorder = start(game);
                if (recorder == null) continue;
            }
            try {
                recorder.tick();
            } catch (RuntimeException ex) {
                Debug.warn("The recording of the game in arena '" + game.getArena().getName() + "' has been stopped", ex);
                recorders.remove(game);
                continue;
            }
            if (game.getState() == GameState.ENDED) {
                if (ending.merge(game, 1, Integer::sum) >= END_TICKS) finish(recorder);
            } else if (age % 20 == 0 && game instanceof VaroGame varo
                    && !varo.today().equals(recorder.getRecording().getInfo().day())) {
                // the next day gets its own recording
                finish(recorder);
            } else if (age % SAVE_INTERVAL == 0 && recorder.isDirty()) {
                recorder.saved();
                save(recorder.getRecording().copy(), null);
            }
        }
    }

    /**
     * @return the recorder of the game, null if it can't be recorded
     */
    @Nullable
    private Recorder start(Game game) {
        World world = game.getGameWorld().getWorld();
        if (world == null) return null;
        Arena arena = game.getArena();
        String id;
        String day = null;
        String startDay = null;
        Recording recording = null;
        if (game instanceof VaroGame varo) {
            day = varo.today();
            // it begins right now
            startDay = varo.getStartDay() == null ? day : varo.getStartDay();
            id = arena.getName() + VARO_DAY + day;
            recording = suspended.remove(key(id));
            suspended.keySet().removeIf(old -> old.startsWith(key(arena.getName() + VARO_DAY)));
            File file = getFile(id);
            if (recording == null && file.isFile()) {
                // the server was restarted today
                try {
                    synchronized (fileLock) {
                        recording = Recording.read(file);
                    }
                } catch (IOException | RuntimeException ex) {
                    Debug.warn("Couldn't read the recording " + file.getName() + " to go on with it - it is replaced", ex);
                }
            }
        } else {
            String name = arena.getName() + "-" + LocalDateTime.now().format(STAMP);
            id = name;
            for (int number = 2; getFile(id).exists() || isRecorded(id); number++) {
                id = name + "-" + number;
            }
        }
        if (recording == null) {
            Location center = arena.getCenter(world);
            recording = new Recording(new Recording.Info(id, arena.getName(), game.getType(),
                    arena.isGenerated() ? null : arena.getMapName(), world.getSeed(), System.currentTimeMillis(), 0, day,
                    startDay, center.getX(), center.getY(), center.getZ(), null, List.of()));
        }
        Recorder recorder = new Recorder(game, recording);
        recorders.put(game, recorder);
        return recorder;
    }

    private boolean isRecorded(String id) {
        return recorders.values().stream().anyMatch(recorder -> recorder.getRecording().getId().equalsIgnoreCase(id));
    }

    /**
     * the game begins (the countdown is over): its recording starts with the first message
     */
    public void begin(Game game) {
        try {
            if (!records(game)) return;
            recorders.remove(game);
            if (game instanceof VaroGame varo) {
                // started again on the same day (after a reset)
                String id = game.getArena().getName() + VARO_DAY + varo.today();
                suspended.remove(key(id));
                if (!playbacks.containsKey(key(id))) deleteFile(id);
            }
            Recorder recorder = start(game);
            if (recorder != null) recorder.tick();
        } catch (RuntimeException ex) {
            Debug.warn("Couldn't record the game in arena '" + game.getArena().getName() + "'", ex);
            recorders.remove(game);
        }
    }

    /**
     * the game stops: its recording is saved
     */
    public void stop(Game game) {
        Recorder recorder = recorders.get(game);
        if (recorder == null) return;
        try {
            finish(recorder);
        } catch (RuntimeException ex) {
            Debug.warn("Couldn't save the recording of the game in arena '" + game.getArena().getName() + "'", ex);
            recorders.remove(game);
        }
    }

    /**
     * @return true while the game is recorded
     */
    public boolean isRecording(Game game) {
        return recorders.containsKey(game);
    }

    private void finish(Recorder recorder) {
        Game game = recorder.getGame();
        recorders.remove(game);
        ending.remove(game);
        recorder.finish();
        Recording recording = recorder.getRecording();
        if (recording.getTicks() < MIN_TICKS || recording.getActors().isEmpty()) return;
        String id = recording.getId();
        // a Varo that is stopped (e.g. the server restarts) goes on today
        boolean goesOn = game instanceof VaroGame varo && game.getState() == GameState.RUNNING
                && varo.today().equals(recording.getInfo().day());
        if (goesOn) suspended.put(key(id), recording);
        List<Player> audience = new ArrayList<>();
        if (game.getState() == GameState.ENDED) {
            for (GamePlayer member : game.getMembers()) {
                if (member.getPlayer().hasPermission(PERMISSION)) audience.add(member.getPlayer());
            }
        }
        String arena = game.getArena().getName();
        save(goesOn ? recording.copy() : recording, () -> {
            prune(arena);
            for (Player player : audience) {
                if (player.isOnline()) player.sendMessage(I.prefixed(player, LangText.REPLAY_SAVED, id));
            }
        });
    }

    /**
     * writes the recording (it must not change anymore) - not on the main thread while the plugin is enabled
     *
     * @param then runs on the main thread when the file is written (not if the plugin is disabled)
     */
    private void save(Recording recording, @Nullable Runnable then) {
        File file = getFile(recording.getId());
        Runnable write = () -> {
            try {
                synchronized (fileLock) {
                    recording.write(file);
                }
            } catch (IOException | RuntimeException ex) {
                Debug.warn("Couldn't save the recording " + file.getName(), ex);
                return;
            }
            infos.remove(file.getName());
            if (then == null || !plugin.isEnabled()) return;
            try {
                Bukkit.getScheduler().runTask(plugin, then);
            } catch (RuntimeException ex) {
                // disabled in the meantime
            }
        };
        if (plugin.isEnabled()) {
            executor.execute(write);
        } else {
            write.run();
        }
    }

    @Nullable
    private Recorder recorder(World world) {
        if (recorders.isEmpty()) return null;
        for (Recorder recorder : recorders.values()) {
            if (world.equals(recorder.getGame().getGameWorld().getWorld())) return recorder;
        }
        return null;
    }

    /**
     * @return the recorder of the game the player plays in
     */
    @Nullable
    private Recorder recorder(Player player) {
        if (recorders.isEmpty()) return null;
        Game game = plugin.getUser(player).getGame();
        return game == null ? null : recorders.get(game);
    }

    /**
     * a message to everybody in the game
     */
    public void message(Game game, Component message) {
        Recorder recorder = recorders.get(game);
        if (recorder != null) recorder.message(message);
    }

    /**
     * the plugin is about to change the block (changes by players and the world are recorded by the events)
     */
    public void block(Block block) {
        Recorder recorder = recorder(block.getWorld());
        if (recorder != null) recorder.block(block, block.getBlockData());
    }

    private void blocks(World world, Iterable<Block> blocks) {
        Recorder recorder = recorder(world);
        if (recorder == null) return;
        for (Block block : blocks) {
            recorder.block(block, block.getBlockData());
        }
    }

    // ---------------------------------------------------------------- files

    /**
     * @return the recordings, the newest one first
     */
    public List<Recording.Info> list() {
        File[] files = getFolder().listFiles(file -> file.isFile() && file.getName().endsWith(Recording.EXTENSION));
        if (files == null) return List.of();
        List<Recording.Info> list = new ArrayList<>();
        for (File file : files) {
            Recording.Info info = info(file);
            if (info != null) list.add(info);
        }
        list.sort(Comparator.comparingLong(Recording.Info::start).reversed());
        return list;
    }

    @Nullable
    private Recording.Info info(File file) {
        long modified = file.lastModified();
        Cached cached = infos.get(file.getName());
        if (cached != null && cached.modified() == modified) return cached.info();
        try {
            Recording.Info info;
            synchronized (fileLock) {
                info = Recording.readInfo(file);
            }
            // the file may have been renamed
            String name = file.getName().substring(0, file.getName().length() - Recording.EXTENSION.length());
            if (!info.id().equalsIgnoreCase(name)) info = null;
            infos.put(file.getName(), new Cached(modified, info));
            return info;
        } catch (IOException | RuntimeException ex) {
            Debug.warn("Couldn't read the recording " + file.getName() + ": " + ex);
            // not again every time the recordings are listed
            infos.put(file.getName(), new Cached(modified, null));
            return null;
        }
    }

    /**
     * @return the recording with this id, null if there is none
     */
    @Nullable
    public Recording.Info find(String id) {
        if (!ID.matcher(id).matches()) return null;
        File file = getFile(id);
        return file.isFile() ? info(file) : null;
    }

    /**
     * @return false for the recordings of a Varo that is still running: only admins see where the others are
     */
    public boolean canWatch(CommandSender sender, Recording.Info info) {
        if (info.type() != GameType.VARO || sender.hasPermission(SurvivalGames.ADMIN_PERMISSION)) return true;
        Arena arena = plugin.getArenaManager().get(info.arena());
        Game game = arena == null ? null : plugin.getGameManager().getGame(arena);
        String startDay;
        if (game instanceof VaroGame varo) {
            if (game.getState() != GameState.STARTING && game.getState() != GameState.RUNNING) return true;
            startDay = varo.getStartDay();
        } else {
            // stopped for now (/sg game <arena> stop): it goes on later
            File saved = arena == null || arena.getType() != GameType.VARO ? null : VaroGame.getFile(arena);
            if (saved == null || !saved.isFile()) return true;
            YamlConfiguration data = YamlConfiguration.loadConfiguration(saved);
            String state = data.getString("state", "");
            if (!state.equals(GameState.STARTING.name()) && !state.equals(GameState.RUNNING.name())) return true;
            startDay = data.getString("start-day");
        }
        // older: a Varo that was played in this arena before
        return startDay != null && info.day() != null && info.day().compareTo(startDay) < 0;
    }

    private boolean isInUse(String id) {
        return playbacks.containsKey(key(id)) || loading.containsKey(key(id)) || isRecorded(id);
    }

    /**
     * @return false if the recording is played or recorded right now (or there is none)
     */
    public boolean delete(String id) {
        if (find(id) == null || isInUse(id)) return false;
        suspended.remove(key(id));
        return deleteFile(id);
    }

    private boolean deleteFile(String id) {
        File file = getFile(id);
        infos.remove(file.getName());
        synchronized (fileLock) {
            return !file.exists() || file.delete();
        }
    }

    /**
     * deletes the oldest recordings of the arena (replays.keep)
     */
    private void prune(String arena) {
        int keep = config().getInt("replays.keep", 10);
        if (keep <= 0) return;
        int kept = 0;
        for (Recording.Info info : list()) {
            if (!info.arena().equalsIgnoreCase(arena) || ++kept <= keep || isInUse(info.id())) continue;
            if (!deleteFile(info.id())) Debug.warn("Couldn't delete the recording " + info.id());
        }
    }

    // ---------------------------------------------------------------- watching

    /**
     * @return the replay the player watches, null if none
     */
    @Nullable
    public Playback getPlayback(Player player) {
        return viewers.get(player.getUniqueId());
    }

    public List<Playback> getPlaybacks() {
        return List.copyOf(playbacks.values());
    }

    /**
     * the player watches the recording (he got a message if he can't) - it is read first, so he may have to wait a
     * moment
     */
    public void watch(Player player, String id) {
        GamePlayer gamePlayer = plugin.getUser(player);
        Game current = gamePlayer.getGame();
        if (current != null && (current.getState() == GameState.ENDED || gamePlayer.isSpectator())) {
            // nothing to lose anymore: the game is over (he waits to be sent back) or he only watches it
            if (find(id) == null) {
                player.sendMessage(I.prefixed(player, LangText.REPLAY_NOT_FOUND, id));
                return;
            }
            current.leave(gamePlayer, Game.LeaveReason.KICKED);
        }
        if (gamePlayer.getGame() != null) {
            player.sendMessage(I.prefixed(player, LangText.ALREADY_IN_GAME, gamePlayer.getGame().getArena().getName()));
            return;
        }
        if (plugin.getArenaManager().isEditing(player)) {
            player.sendMessage(I.prefixed(player, LangText.EDITING_ARENA));
            return;
        }
        Recording.Info info = find(id);
        if (info == null) {
            player.sendMessage(I.prefixed(player, LangText.REPLAY_NOT_FOUND, id));
            return;
        }
        if (!canWatch(player, info)) {
            player.sendMessage(I.prefixed(player, LangText.REPLAY_RUNNING_VARO));
            return;
        }
        String key = key(info.id());
        Playback watched = getPlayback(player);
        if (watched != null) {
            if (key(watched.getId()).equals(key)) {
                player.sendMessage(I.prefixed(player, LangText.REPLAY_CONTROLS));
                return;
            }
            leave(player);
        }
        Playback playback = playbacks.get(key);
        if (playback != null) {
            enter(player, playback);
            return;
        }
        for (List<UUID> others : loading.values()) {
            others.remove(player.getUniqueId());
        }
        List<UUID> waiting = loading.get(key);
        if (waiting == null && playbacks.size() + loading.size() >= Math.max(1, config().getInt("replays.max-playbacks", 2))) {
            player.sendMessage(I.prefixed(player, LangText.REPLAY_BUSY));
            return;
        }
        player.sendMessage(I.prefixed(player, LangText.REPLAY_LOADING, info.id()));
        if (waiting != null) {
            waiting.add(player.getUniqueId());
            return;
        }
        loading.put(key, new ArrayList<>(List.of(player.getUniqueId())));
        load(info, key);
    }

    /**
     * reads the recording and gets its world ready (not on the main thread), then opens it
     */
    private void load(Recording.Info info, String key) {
        File file = getFile(info.id());
        File backup = info.type() != GameType.VARO || info.day() == null ? null
                : new File(FileManager.getPluginFolder(), "backups/" + info.arena() + "/" + info.day() + ".zip");
        boolean fromBackup = backup != null && backup.isFile();
        File map = info.map() == null ? null : new File(FileManager.getMapsFolder(), info.map());
        if (!fromBackup && map != null && !map.isDirectory()) {
            fail(key, "the map '" + info.map() + "' doesn't exist anymore", null);
            return;
        }
        GameWorld gameWorld = new GameWorld(fromBackup ? null : map, info.seed(),
                "replay_" + (++worlds) + "_" + Integer.toHexString((int) (System.nanoTime() & 0xFFFF)), false);
        File folder = gameWorld.getWorldFolder();
        executor.execute(() -> {
            Recording recording;
            try {
                synchronized (fileLock) {
                    recording = Recording.read(file);
                }
                if (fromBackup) {
                    FileManager.deleteDirectory(folder);
                    unzipWorld(backup, folder);
                }
            } catch (IOException | RuntimeException ex) {
                FileManager.deleteDirectory(folder);
                run(() -> fail(key, String.valueOf(ex.getMessage()), ex));
                return;
            }
            run(() -> open(key, recording, gameWorld, fromBackup));
        });
    }

    private void run(Runnable task) {
        try {
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, task);
        } catch (RuntimeException ex) {
            // disabled in the meantime
        }
    }

    /**
     * unpacks the world of a backup of a Varo (world/...) into the folder - without its entities (the animals of that
     * morning would walk through the replay) and without the id of the world (it is the one of the Varo)
     */
    static void unzipWorld(File backup, File folder) throws IOException {
        Path root = folder.toPath().toAbsolutePath().normalize();
        try (ZipFile zip = new ZipFile(backup)) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith("world/")) continue;
                name = name.substring("world/".length());
                if (name.startsWith("entities/") || name.equals("data/paper/metadata.dat")) continue;
                Path target = root.resolve(name).normalize();
                // a zip file from somewhere else must not write outside of the world
                if (!target.startsWith(root)) throw new IOException("Invalid entry " + entry.getName());
                if (target.getParent() != null) Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Files.createDirectories(root);
    }

    private void fail(String key, String reason, @Nullable Throwable exception) {
        List<UUID> waiting = loading.remove(key);
        if (exception != null) Debug.warn("Couldn't load the replay " + key, exception);
        if (waiting == null) return;
        for (UUID uuid : waiting) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.sendMessage(I.prefixed(player, LangText.REPLAY_LOAD_FAILED, reason));
        }
    }

    private void open(String key, Recording recording, GameWorld gameWorld, boolean fromBackup) {
        List<UUID> waiting = loading.get(key);
        if (waiting == null) {
            FileManager.deleteDirectory(gameWorld.getWorldFolder());
            return;
        }
        World world;
        try {
            if (fromBackup) gameWorld.setPrepared();
            world = gameWorld.load();
        } catch (RuntimeException ex) {
            fail(key, String.valueOf(ex.getMessage()), ex);
            return;
        }
        loading.remove(key);
        setup(world);
        Playback playback;
        try {
            playback = new Playback(plugin, recording, gameWorld, world);
        } catch (RuntimeException ex) {
            gameWorld.unload();
            loading.put(key, waiting);
            fail(key, String.valueOf(ex.getMessage()), ex);
            return;
        }
        playbacks.put(key, playback);
        boolean varo = recording.getInfo().type() == GameType.VARO;
        for (UUID uuid : waiting) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || plugin.getUser(player).getGame() != null || getPlayback(player) != null
                    || plugin.getArenaManager().isEditing(player)) continue;
            enter(player, playback);
            if (varo && !fromBackup) player.sendMessage(I.prefixed(player, LangText.REPLAY_NO_BACKUP));
        }
        if (playback.getViewers().isEmpty()) close(playback);
    }

    /**
     * nothing happens in the world of a replay but what was recorded
     */
    private static void setup(World world) {
        try {
            world.setGameRule(GameRules.SPAWN_MOBS, false);
            world.setGameRule(GameRules.SPAWN_MONSTERS, false);
            world.setGameRule(GameRules.ADVANCE_TIME, false);
            world.setGameRule(GameRules.ADVANCE_WEATHER, false);
            world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        } catch (RuntimeException ex) {
            Debug.warn("Couldn't set the game rules of the world of a replay: " + ex);
        }
    }

    private void enter(Player player, Playback playback) {
        GamePlayer gamePlayer = plugin.getUser(player);
        if (plugin.getBuildMode().isBuilding(player)) plugin.getBuildMode().toggle(player);
        gamePlayer.getStorage().store();
        gamePlayer.getStorage().cleanPlayer();
        player.setGameMode(GameMode.SPECTATOR);
        plugin.getEssentials().joinGame(player, true);
        viewers.put(player.getUniqueId(), playback);
        plugin.getTelemetry().used(Telemetry.Feature.REPLAY);
        playback.add(player);
        Recording.Info info = playback.getInfo();
        player.sendMessage(I.prefixed(player, LangText.REPLAY_STARTED, describe(info), Game.formatTime(info.seconds())));
        player.sendMessage(I.prefixed(player, LangText.REPLAY_CONTROLS));
    }

    /**
     * @return e.g. "Forest" or "Forest (day 3)"
     */
    public static Component describe(Recording.Info info) {
        if (info.type() == GameType.VARO && info.dayNumber() > 0) {
            return I.i18n(LangText.REPLAY_NAME_VARO, info.arena(), info.dayNumber());
        }
        return Component.text(info.arena());
    }

    /**
     * the player stops watching (or waiting for) a replay and gets his state from before back
     *
     * @return false if he didn't watch one
     */
    public boolean leave(Player player) {
        for (List<UUID> waiting : loading.values()) {
            waiting.remove(player.getUniqueId());
        }
        Playback playback = viewers.remove(player.getUniqueId());
        if (playback == null) return false;
        playback.remove(player);
        plugin.getUser(player).getStorage().restore();
        plugin.getEssentials().leaveGame(player);
        if (player.getWorld().equals(playback.getWorld())) {
            // no saved location (e.g. a crash) - out of the world of the replay
            player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
        }
        if (playback.getViewers().isEmpty()) close(playback);
        return true;
    }

    private void close(Playback playback) {
        if (!playbacks.remove(key(playback.getId()), playback)) return;
        for (Player viewer : new ArrayList<>(playback.getViewers())) {
            leave(viewer);
        }
        playback.close();
        // somebody who was teleported there
        for (Player player : playback.getWorld().getPlayers()) {
            player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
        }
        if (!playback.getGameWorld().unload()) {
            Debug.warn("The world of the replay " + playback.getId() + " stays loaded");
        }
    }

    /**
     * the plugin is disabled (after the games were stopped): the viewers get their state back
     */
    public void close() {
        task.cancel();
        for (Recorder recorder : new ArrayList<>(recorders.values())) {
            stop(recorder.getGame());
        }
        loading.clear();
        for (Playback playback : new ArrayList<>(playbacks.values())) {
            close(playback);
        }
    }

    // ---------------------------------------------------------------- events of the viewers

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        leave(e.getPlayer());
    }

    /**
     * teleported out of the world of the replay (e.g. by another plugin)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Playback playback = getPlayback(e.getPlayer());
        if (playback != null && !playback.getWorld().equals(e.getTo().getWorld())) leave(e.getPlayer());
    }

    /**
     * the viewer sneaks to leave the body he watched from (not: the body is gone)
     */
    @EventHandler
    public void onStopSpectating(PlayerStopSpectatingEntityEvent e) {
        Playback playback = getPlayback(e.getPlayer());
        if (playback != null && e.getSpectatorTarget().isValid()) playback.stopFollowing(e.getPlayer());
    }

    // ---------------------------------------------------------------- events of the games

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerArmSwingEvent e) {
        Recorder recorder = recorder(e.getPlayer());
        if (recorder != null) recorder.swing(e.getPlayer(), e.getHand() == EquipmentSlot.OFF_HAND);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        Recorder recorder = recorder(player);
        if (recorder != null) recorder.hurt(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Recorder recorder = recorder(e.getPlayer());
        if (recorder != null) recorder.death(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player player)) return;
        Recorder recorder = recorder(player);
        if (recorder != null) recorder.launch(player, e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLightning(LightningStrikeEvent e) {
        Recorder recorder = recorder(e.getWorld());
        if (recorder != null) recorder.lightning(e.getLightning().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Recorder recorder = recorder(e.getBlock().getWorld());
        if (recorder == null) return;
        // the block is placed already
        if (e instanceof BlockMultiPlaceEvent multi) {
            for (BlockState replaced : multi.getReplacedBlockStates()) {
                recorder.block(replaced.getBlock(), replaced.getBlockData());
            }
        } else {
            recorder.block(e.getBlock(), e.getBlockReplacedState().getBlockData());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        withNeighbors(e.getBlock());
    }

    /**
     * the block and what hangs on it (torches, doors, ...)
     */
    private void withNeighbors(Block block) {
        Recorder recorder = recorder(block.getWorld());
        if (recorder == null) return;
        recorder.block(block, block.getBlockData());
        for (BlockFace face : NEIGHBORS) {
            Block neighbor = block.getRelative(face);
            recorder.block(neighbor, neighbor.getBlockData());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        withNeighbors(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        block(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        block(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        block(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent e) {
        block(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        block(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        block(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        block(e.getToBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPrime(TNTPrimeEvent e) {
        block(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) {
        blocks(e.getBlock().getWorld(), e.blockList());
        block(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        blocks(e.getLocation().getWorld(), e.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChange(EntityChangeBlockEvent e) {
        withNeighbors(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent e) {
        block(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent e) {
        block(e.getBlock());
    }

    /**
     * doors, levers, buttons, cake, ...
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        Block block = e.getClickedBlock();
        if (block == null || e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.PHYSICAL) return;
        withNeighbors(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        piston(e.getBlock(), e.getBlocks(), e.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent e) {
        piston(e.getBlock(), e.getBlocks(), e.getDirection());
    }

    private void piston(Block piston, List<Block> moved, BlockFace direction) {
        Recorder recorder = recorder(piston.getWorld());
        if (recorder == null) return;
        withNeighbors(piston);
        for (Block block : moved) {
            recorder.block(block, block.getBlockData());
            Block target = block.getRelative(direction);
            recorder.block(target, target.getBlockData());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent e) {
        states(e.getWorld(), e.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent e) {
        states(e.getBlock().getWorld(), e.getBlocks());
    }

    /**
     * @param states what the blocks are going to be
     */
    private void states(World world, List<BlockState> states) {
        Recorder recorder = recorder(world);
        if (recorder == null) return;
        for (BlockState state : states) {
            Block block = state.getBlock();
            recorder.block(block, block.getBlockData());
        }
    }

}
