package com.christian34.survivalgames.replay;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.arena.GameWorld;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.game.VaroGame;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Mannequin;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The replays: games are recorded while they run, the recordings are saved and played in their own world.
 *
 * @author Christian34
 */
class ReplayTest extends PluginTestBase {
    private SurvivalGame game;
    private PlayerMock steve;
    private PlayerMock alex;

    @BeforeEach
    void setUpGame() {
        plugin.getStats().setExecutor(Runnable::run);
        plugin.getReplays().setExecutor(Runnable::run);
        config().set("survival-games.lobby-countdown", 1);
        config().set("survival-games.start-countdown", 1);
        config().set("survival-games.grace-period", 0);
        config().set("survival-games.ending-seconds", 60);
        game = (SurvivalGame) createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
    }

    private Replays replays() {
        return plugin.getReplays();
    }

    private void begin() {
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        for (int tick = 0; tick < 20 * 10 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());
        messages(steve);
        messages(alex);
    }

    /**
     * what follows on the main thread when a recording has been written or read
     */
    private void finishTasks() {
        server.getScheduler().performOneTick();
    }

    /**
     * a round of 8 seconds: Steve walks and places a block, then he kills Alex
     *
     * @return the id of its recording
     */
    private String playRound() {
        begin();
        seconds(3);
        Location location = steve.getLocation().add(3, 0, 0);
        steve.teleport(location);
        Block block = location.getBlock().getRelative(0, 2, 0);
        replays().block(block);
        block.setType(Material.GOLD_BLOCK);
        steve.getInventory().setItemInMainHand(ItemStack.of(Material.IRON_SWORD));
        seconds(5);
        alex.setKiller(steve);
        alex.setHealth(0);
        assertEquals(GameState.ENDED, game.getState());
        seconds(4);
        finishTasks();
        List<String> ids = replays().list().stream().map(Recording.Info::id)
                .filter(id -> id.startsWith("arena1-2")).toList();
        assertEquals(1, ids.size());
        return ids.getFirst();
    }

    private Recording read(String id) {
        return assertDoesNotThrow(() -> Recording.read(new File(Replays.getFolder(), id + Recording.EXTENSION)));
    }

    private static <T extends Action> List<T> actions(Recording recording, Class<T> type) {
        return recording.getActions().stream().filter(type::isInstance).map(type::cast).toList();
    }

    /**
     * @return the replay the viewer watches after the command
     */
    private Playback watch(PlayerMock viewer, String id) {
        execute(viewer, "sg replay play " + id);
        finishTasks();
        Playback playback = replays().getPlayback(viewer);
        assertNotNull(playback, () -> "no replay: " + messages(viewer));
        return playback;
    }

    // ---------------------------------------------------------------- file

    @Test
    void recordingsAreWrittenAndRead() throws IOException {
        UUID uuid = UUID.randomUUID();
        Recording recording = new Recording(new Recording.Info("arena1-test", "arena1", GameType.VARO, "map1", 42L,
                1000L, 0, "2026-10-10", "2026-10-08", 100.5, 64, -200.5, null, List.of()));
        int actor = recording.actor(new Recording.Actor(uuid, "Steve", "Wolves", "red", "skin", null));
        byte[] item = {1, 2, 3};
        List<Action> actions = List.of(
                new Action.Clock(1, 123456789L),
                new Action.Spawn(1, actor, 110.5, 70, -190.25, 90, -45),
                new Action.Pose(1, actor, "SNEAKING", true, false),
                new Action.Equip(1, actor, 0, item),
                new Action.Equip(1, actor, 5, null),
                new Action.Health(1, actor, 17.5f),
                new Action.Border(1, 0.5, 0.5, 200),
                new Action.Sky(1, 6000, true),
                new Action.Move(2, actor, 111, 70.5, -190, 180, 45),
                new Action.Swing(3, actor, true),
                new Action.Hurt(3, actor),
                new Action.SetBlock(40, 110, -60, -190, "minecraft:air", "minecraft:oak_log[axis=y]"),
                new Action.SetBlock(41, 110, 300, -190, "minecraft:oak_log[axis=y]", "minecraft:air"),
                new Action.Message(41, "{\"text\":\"Hello\"}"),
                new Action.PropSpawn(50, 7, item, 111, 71, -190),
                new Action.PropMove(51, 7, 112, 71.5, -190),
                new Action.PropRemove(52, 7),
                new Action.Lightning(60, 100.5, 64, -200.5),
                new Action.Despawn(5000, actor, true));
        recording.getActions().addAll(actions);
        recording.setTicks(5000);
        recording.setWinner("Wolves");
        File file = new File(worlds, "test.sgr");

        recording.write(file);
        Recording read = Recording.read(file);
        Recording.Info info = Recording.readInfo(file);

        assertEquals(recording.getInfo(), info);
        assertEquals(read.getInfo(), info);
        assertEquals(3, info.dayNumber());
        assertEquals(250, info.seconds());
        assertEquals("Wolves", info.winner());
        assertEquals(new Recording.Actor(uuid, "Steve", "Wolves", "red", "skin", null), info.actors().getFirst());
        assertEquals(actions.size(), read.getActions().size());
        for (int i = 0; i < actions.size(); i++) {
            Action expected = actions.get(i);
            Action actual = read.getActions().get(i);
            switch (expected) {
                case Action.Equip equip -> {
                    Action.Equip other = assertInstanceOf(Action.Equip.class, actual);
                    assertEquals(equip.slot(), other.slot());
                    assertArrayEquals(equip.item(), other.item());
                }
                case Action.PropSpawn prop -> {
                    Action.PropSpawn other = assertInstanceOf(Action.PropSpawn.class, actual);
                    assertEquals(prop.prop(), other.prop());
                    assertArrayEquals(prop.item(), other.item());
                    assertEquals(prop.x(), other.x());
                }
                default -> assertEquals(expected, actual);
            }
        }
    }

    @Test
    void otherFilesAreNotRecordings() throws IOException {
        File file = new File(Replays.getFolder(), "broken.sgr");
        assertTrue(file.getParentFile().mkdirs() || file.getParentFile().isDirectory());
        java.nio.file.Files.writeString(file.toPath(), "not a recording");

        assertThrows(IOException.class, () -> Recording.read(file));
        assertTrue(replays().list().isEmpty());
        assertNull(replays().find("broken"));
        assertNull(replays().find("../config"), "no paths");
    }

    // ---------------------------------------------------------------- recording

    @Test
    void aRoundIsRecorded() {
        String id = playRound();

        assertTrue(id.startsWith("arena1-"), id);
        assertFalse(replays().isRecording(game));
        Recording.Info info = replays().find(id);
        assertNotNull(info);
        assertEquals("arena1", info.arena());
        assertEquals(GameType.SURVIVAL_GAMES, info.type());
        assertEquals("arena1", info.map());
        assertEquals(List.of("Alex", "Steve"), info.actors().stream().map(Recording.Actor::name).sorted().toList());
        assertNotNull(info.winner());
        // 8 seconds and the 3 seconds the winner celebrates
        assertEquals(11, info.seconds(), 1);
        Recording recording = read(id);
        assertEquals(2, actions(recording, Action.Spawn.class).size());
        assertFalse(actions(recording, Action.Move.class).isEmpty());
        Action.SetBlock block = actions(recording, Action.SetBlock.class).getFirst();
        assertEquals("minecraft:air", block.before());
        assertEquals("minecraft:gold_block", block.after());
        // Alex died, Steve was still there when the recording ended
        List<Action.Despawn> despawns = actions(recording, Action.Despawn.class);
        assertEquals(List.of(true, false), despawns.stream().map(Action.Despawn::dead).toList());
        assertFalse(actions(recording, Action.Message.class).isEmpty());
        assertFalse(actions(recording, Action.Equip.class).isEmpty());
        assertFalse(actions(recording, Action.Border.class).isEmpty());
        // the players are told
        assertContains(messages(steve), "This game has been recorded");
    }

    @Test
    void recordingCanBeTurnedOff() {
        config().set("replays.enabled", false);

        begin();
        seconds(8);

        assertFalse(replays().isRecording(game));
        plugin.getGameManager().removeGame(game);
        finishTasks();
        assertTrue(replays().list().isEmpty());
    }

    @Test
    void onlyTheChosenGameTypesAreRecorded() {
        config().set("replays.games", List.of("varo"));

        begin();

        assertFalse(replays().isRecording(game));
    }

    @Test
    void aStoppedRoundIsSaved() {
        begin();
        assertTrue(replays().isRecording(game));
        seconds(8);

        plugin.getGameManager().removeGame(game);
        finishTasks();

        assertEquals(1, replays().list().size());
        assertNull(replays().list().getFirst().winner());
    }

    @Test
    void shortRoundsAreNotKept() {
        begin();
        seconds(2);

        plugin.getGameManager().removeGame(game);
        finishTasks();

        assertTrue(replays().list().isEmpty());
    }

    @Test
    void onlyTheNewestRecordingsAreKept() throws IOException {
        config().set("replays.keep", 2);
        for (int i = 1; i <= 2; i++) {
            Recording old = new Recording(new Recording.Info("arena1-old" + i, "arena1", GameType.SURVIVAL_GAMES, "arena1",
                    0, 1000L * i, 200, null, null, 0, 64, 0, null, List.of()));
            old.write(new File(Replays.getFolder(), old.getId() + Recording.EXTENSION));
        }
        Recording other = new Recording(new Recording.Info("arena2-old", "arena2", GameType.SURVIVAL_GAMES, "arena2",
                0, 500L, 200, null, null, 0, 64, 0, null, List.of()));
        other.write(new File(Replays.getFolder(), other.getId() + Recording.EXTENSION));

        String id = playRound();

        assertEquals(List.of(id, "arena1-old2", "arena2-old"), replays().list().stream().map(Recording.Info::id).toList());
    }

    // ---------------------------------------------------------------- watching

    @Test
    void aRecordingIsPlayedInItsOwnWorld() {
        String id = playRound();
        plugin.getGameManager().removeGame(game);
        PlayerMock viewer = addPlayer("Viewer");
        viewer.getInventory().addItem(ItemStack.of(Material.DIAMOND));
        Location before = viewer.getLocation();

        Playback playback = watch(viewer, id);

        World replayWorld = playback.getWorld();
        assertTrue(GameWorld.isPluginWorld(replayWorld));
        assertEquals(replayWorld, viewer.getWorld());
        assertEquals(GameMode.SPECTATOR, viewer.getGameMode());
        assertTrue(viewer.getInventory().isEmpty());
        assertContains(messages(viewer), "Replay of arena1");
        // both players stand on their spawns
        assertEquals(2, replayWorld.getEntitiesByClass(Mannequin.class).size());
        assertEquals(List.of("Alex", "Steve"), playback.getPresent().stream().map(Recording.Actor::name).sorted().toList());

        // the whole recording
        seconds(playback.getLength() / 20 + 2);
        assertEquals(playback.getLength(), playback.getTick());
        assertTrue(playback.isPaused());
        assertContains(messages(viewer), "The replay is over");
        assertTrue(replayWorld.getEntitiesByClass(Mannequin.class).isEmpty());
        Action.SetBlock block = actions(read(id), Action.SetBlock.class).getFirst();
        assertEquals(Material.GOLD_BLOCK, replayWorld.getBlockAt(block.x(), block.y(), block.z()).getType());

        // back to the beginning: the block isn't there yet
        execute(viewer, "sg replay goto 0:01");
        assertEquals(20, playback.getTick());
        assertEquals(Material.AIR, replayWorld.getBlockAt(block.x(), block.y(), block.z()).getType());
        assertEquals(2, replayWorld.getEntitiesByClass(Mannequin.class).size());

        execute(viewer, "sg replay leave");
        assertNull(replays().getPlayback(viewer));
        assertEquals(before.getWorld(), viewer.getWorld());
        assertEquals(GameMode.SURVIVAL, viewer.getGameMode());
        assertTrue(viewer.getInventory().contains(Material.DIAMOND));
        assertTrue(replays().getPlaybacks().isEmpty());
        assertNull(Bukkit.getWorld(replayWorld.getName()), "the world of the replay is unloaded");
    }

    @Test
    void theViewersControlTheReplay() {
        String id = playRound();
        plugin.getGameManager().removeGame(game);
        PlayerMock viewer = addPlayer("Viewer");
        PlayerMock other = addPlayer("Other");
        Playback playback = watch(viewer, id);
        // the same replay
        assertSame(playback, watch(other, id));
        assertEquals(1, replays().getPlaybacks().size());
        messages(viewer);
        messages(other);

        execute(viewer, "sg replay pause");
        assertTrue(playback.isPaused());
        assertContains(messages(other), "Viewer has paused the replay");
        int tick = playback.getTick();
        seconds(2);
        assertEquals(tick, playback.getTick());

        execute(viewer, "sg replay pause");
        execute(viewer, "sg replay speed 4");
        assertEquals(4, playback.getSpeed());
        tick = playback.getTick();
        seconds(1);
        assertEquals(tick + 80, playback.getTick(), 4);

        execute(viewer, "sg replay speed 3");
        assertContains(messages(viewer), "Invalid speed");
        execute(viewer, "sg replay goto soon");
        assertContains(messages(viewer), "Invalid time");

        execute(viewer, "sg replay speed 0.5");
        execute(viewer, "sg replay goto 5");
        assertEquals(100, playback.getTick(), 1);
        execute(viewer, "sg replay skip -3");
        assertEquals(40, playback.getTick(), 1);
        execute(viewer, "sg replay skip 10000");
        assertEquals(playback.getLength(), playback.getTick());

        // the last one leaves: the replay is closed
        execute(viewer, "sg leave");
        assertEquals(1, replays().getPlaybacks().size());
        other.disconnect();
        assertTrue(replays().getPlaybacks().isEmpty());
    }

    @Test
    void theMenusShowTheReplaysAndTheControls() {
        String id = playRound();
        plugin.getGameManager().removeGame(game);
        PlayerMock viewer = addPlayer("Viewer");

        execute(viewer, "sg replay");
        assertNotNull(viewer.getOpenInventory().getTopInventory().getItem(4), "the recording");
        viewer.closeInventory();
        execute(viewer, "sg replay list");
        assertContains(messages(viewer), "arena1");

        Playback playback = watch(viewer, id);
        execute(viewer, "sg replay");
        assertEquals(Material.BARRIER, viewer.getOpenInventory().getTopInventory().getItem(6).getType());
        viewer.closeInventory();
        execute(viewer, "sg replay players");
        assertEquals(Material.PLAYER_HEAD, viewer.getOpenInventory().getTopInventory().getItem(3).getType());
        viewer.closeInventory();
        assertNotNull(playback);
    }

    @Test
    void joiningAGameLeavesTheReplay() {
        String id = playRound();
        plugin.getGameManager().restart(game);
        PlayerMock viewer = addPlayer("Viewer");
        watch(viewer, id);

        execute(viewer, "sg join arena1");

        assertNull(replays().getPlayback(viewer));
        assertNotNull(plugin.getUser(viewer).getGame());
        assertTrue(replays().getPlaybacks().isEmpty());
    }

    @Test
    void playersInAGameCantWatch() {
        String id = playRound();
        plugin.getGameManager().restart(game);
        var next = plugin.getGameManager().getGame(game.getArena());
        next.join(plugin.getUser(steve));

        execute(steve, "sg replay play " + id);
        finishTasks();

        assertNull(replays().getPlayback(steve));
        assertContains(messages(steve), "already in the game");
    }

    @Test
    void theReplayCanBeWatchedWhileTheGameEnds() {
        String id = playRound();
        assertEquals(GameState.ENDED, game.getState());
        assertNotNull(plugin.getUser(steve).getGame());

        watch(steve, id);

        assertNull(plugin.getUser(steve).getGame());
    }

    @Test
    void unknownReplays() {
        execute(steve, "sg replay play nothing");
        assertContains(messages(steve), "There is no replay nothing");
        execute(steve, "sg replay pause");
        assertContains(messages(steve), "You are not watching a replay");
        execute(steve, "sg replay list");
        assertContains(messages(steve), "There are no replays yet");
    }

    @Test
    void onlySoManyReplaysAtTheSameTime() throws IOException {
        config().set("replays.max-playbacks", 1);
        String id = playRound();
        plugin.getGameManager().removeGame(game);
        Recording copy = new Recording(new Recording.Info("arena1-copy", "arena1", GameType.SURVIVAL_GAMES, "arena1",
                0, 1000L, 200, null, null, 0, 64, 0, null, List.of()));
        copy.write(new File(Replays.getFolder(), "arena1-copy" + Recording.EXTENSION));
        PlayerMock viewer = addPlayer("Viewer");
        PlayerMock other = addPlayer("Other");
        watch(viewer, id);

        execute(other, "sg replay play arena1-copy");
        finishTasks();

        assertNull(replays().getPlayback(other));
        assertContains(messages(other), "Too many replays");
    }

    @Test
    void adminsDeleteReplays() {
        String id = playRound();
        plugin.getGameManager().removeGame(game);
        PlayerMock admin = addAdmin("Admin");
        PlayerMock viewer = addPlayer("Viewer");
        watch(viewer, id);

        assertNotNull(execute(viewer, "sg replay delete " + id), "only admins");
        execute(admin, "sg replay delete " + id);
        assertContains(messages(admin), "is being watched");
        execute(viewer, "sg replay leave");
        execute(admin, "sg replay delete " + id);

        assertContains(messages(admin), "has been deleted");
        assertTrue(replays().list().isEmpty());
    }

    @Test
    void theReplaysNeedTheirPermission() {
        PlayerMock guest = addPlayer("Guest");
        guest.addAttachment(plugin, Replays.PERMISSION, false);

        assertNotNull(execute(guest, "sg replay list"));
        assertNull(execute(steve, "sg replay list"));
    }

    @Test
    void disablingThePluginSavesTheRecordingsAndFreesTheViewers() {
        String id = playRound();
        plugin.getGameManager().restart(game);
        PlayerMock viewer = addPlayer("Viewer");
        viewer.getInventory().addItem(ItemStack.of(Material.DIAMOND));
        watch(viewer, id);

        server.getPluginManager().disablePlugin(plugin);

        assertTrue(viewer.getInventory().contains(Material.DIAMOND));
        assertFalse(GameWorld.isPluginWorld(viewer.getWorld()));
    }

    // ---------------------------------------------------------------- Varo

    private VaroGame startVaro(PlayerMock admin) {
        config().set("varo.start-countdown", 1);
        config().set("varo.combat-radius", 0);
        config().set("varo.join-protection", 0);
        VaroGame varo = (VaroGame) createGame("varo1", GameType.VARO, 3);
        execute(admin, "sg join varo1");
        execute(steve, "sg join varo1");
        execute(admin, "sg team create Wolves");
        execute(steve, "sg team create Bears");
        execute(admin, "sg game varo1 start");
        for (int tick = 0; tick < 20 * 5 && varo.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, varo.getState());
        return varo;
    }

    @Test
    void aVaroIsRecordedDayByDay() {
        PlayerMock admin = addAdmin("Admin");
        VaroGame varo = startVaro(admin);
        String today = varo.today();
        seconds(8);
        assertTrue(replays().isRecording(varo));

        varo.setClock(Clock.offset(Clock.systemDefaultZone(), Duration.ofDays(1)));
        seconds(8);
        finishTasks();

        // the first day is done, the second one goes on
        assertTrue(replays().isRecording(varo));
        Recording.Info info = replays().find("varo1-day-" + today);
        assertNotNull(info);
        assertEquals(GameType.VARO, info.type());
        assertEquals(today, info.day());
        assertEquals(1, info.dayNumber());
        assertEquals(2, info.actors().size());
        assertEquals(1, actions(read(info.id()), Action.Clock.class).size());

        // only admins see where the others were while the game runs
        PlayerMock viewer = addPlayer("Viewer");
        execute(viewer, "sg replay play " + info.id());
        finishTasks();
        assertNull(replays().getPlayback(viewer));
        assertContains(messages(viewer), "can be watched when it is over");
        execute(viewer, "sg replay list");
        assertNotContains(messages(viewer), "varo1");
        PlayerMock watcher = addAdmin("Watcher");
        Playback playback = watch(watcher, info.id());
        assertEquals(2, playback.getWorld().getEntitiesByClass(Mannequin.class).size());
        assertContains(messages(watcher), "Replay of varo1 (day 1)");

        // also while the Varo is stopped for now
        execute(watcher, "sg replay leave");
        execute(admin, "sg game varo1 stop");
        finishTasks();
        assertNull(plugin.getGameManager().getGame(varo.getArena()));
        messages(viewer);
        execute(viewer, "sg replay play " + info.id());
        finishTasks();
        assertNull(replays().getPlayback(viewer));
        assertContains(messages(viewer), "can be watched when it is over");
    }

    @Test
    void theRecordingOfAVaroGoesOnAfterARestart() {
        PlayerMock admin = addAdmin("Admin");
        VaroGame varo = startVaro(admin);
        String id = "varo1-day-" + varo.today();
        seconds(8);

        // the server stops
        plugin.getGameManager().removeGame(varo);
        finishTasks();
        Recording.Info saved = replays().find(id);
        assertNotNull(saved);

        VaroGame loaded = (VaroGame) plugin.getGameManager().createGame(varo.getArena());
        execute(steve, "sg join varo1");
        seconds(8);
        plugin.getGameManager().removeGame(loaded);
        finishTasks();

        Recording.Info info = replays().find(id);
        assertNotNull(info);
        assertEquals(1, replays().list().size());
        assertTrue(info.ticks() > saved.ticks() + 100, info.ticks() + " > " + saved.ticks());
        // Steve came, left with the server and came back
        Recording recording = read(id);
        assertEquals(3, actions(recording, Action.Spawn.class).size());
        assertEquals(2, actions(recording, Action.Clock.class).size());
    }

}
