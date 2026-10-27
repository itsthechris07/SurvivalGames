package com.christian34.survivalgames.game;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.integrations.Placeholders;
import com.christian34.survivalgames.network.ServerStatus;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The optional rules of a Varo: peaceful days, inactive players, the daily coordinates, the villager standing in for
 * players who leave the server and the backups.
 *
 * @author Christian34
 */
class VaroRulesTest extends PluginTestBase {
    private VaroGame game;
    private PlayerMock admin;
    private PlayerMock steve;
    private PlayerMock alex;

    @BeforeEach
    void setUpGame() {
        config().set("varo.start-countdown", 1);
        config().set("varo.combat-radius", 0);
        config().set("varo.join-protection", 0);
        game = (VaroGame) createGame("varo1", GameType.VARO, 3);
        admin = addAdmin("Admin");
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
        execute(admin, "sg join varo1");
        execute(steve, "sg join varo1");
        execute(alex, "sg join varo1");
        execute(admin, "sg team create Wolves");
        execute(steve, "sg team create Bears");
        execute(alex, "sg team create Foxes");
    }

    private Participant participant(PlayerMock player) {
        return game.getParticipant(player.getUniqueId());
    }

    private void startGame() {
        execute(admin, "sg game varo1 start");
        for (int tick = 0; tick < 20 * 5 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());
    }

    /**
     * the day of the game begins (1: the day it started) - everybody in the game plays for a second
     */
    private void day(int day) {
        game.setClock(Clock.offset(Clock.systemDefaultZone(), Duration.ofDays(day - 1)));
        seconds(1);
        assertEquals(day, game.getDay());
    }

    // peaceful days

    @Test
    void noPvpDuringThePeacefulDays() {
        config().set("varo.peaceful-days", 2);
        startGame();
        assertContains(messages(steve), "No PvP for the first 2 days - farm and build!");
        assertTrue(game.isPeaceful());
        assertFalse(attack(admin, steve));
        assertTrue(fall(steve), "only other players can't hurt him");

        day(2);
        assertFalse(attack(admin, steve));
        assertNotContains(messages(steve), "The peaceful days are over");

        day(3);
        assertContains(messages(steve), "The peaceful days are over - PvP is on from now on!");
        assertFalse(game.isPeaceful());
        assertTrue(attack(admin, steve));
    }

    @Test
    void pvpFromTheStartByDefault() {
        startGame();
        assertNotContains(messages(steve), "No PvP for the first");
        assertFalse(game.isPeaceful());
        assertTrue(attack(admin, steve));
    }

    // inactive players

    @Test
    void inactivePlayersAreOut() {
        config().set("varo.inactive-days", 2);
        startGame();
        seconds(1);
        execute(alex, "sg leave");
        messages(steve);

        day(2);
        day(3);
        assertTrue(participant(alex).isAlive(), "missed one day so far");
        day(4);
        assertFalse(participant(alex).isAlive());
        assertContains(messages(steve), "Alex hasn't played for 2 days and is out of the game.");
        assertTrue(participant(steve).isAlive(), "he played every day");
        assertEquals(GameState.RUNNING, game.getState());
    }

    @Test
    void playersWhoNeverPlayedAreOutToo() {
        config().set("varo.inactive-days", 2);
        // in a team, but not there when the game starts
        execute(alex, "sg leave");
        startGame();
        day(2);
        assertTrue(participant(alex).isAlive());
        day(3);
        assertFalse(participant(alex).isAlive(), "missed the first two days");
    }

    @Test
    void inactivePlayersStayByDefault() {
        startGame();
        seconds(1);
        execute(alex, "sg leave");
        day(10);
        assertTrue(participant(alex).isAlive());
    }

    // coordinates

    @Test
    void coordinatesAreAnnouncedEveryDay() {
        config().set("varo.coordinates-from-day", 3);
        startGame();
        seconds(1);
        var position = alex.getLocation();
        execute(alex, "sg leave");
        messages(steve);

        day(2);
        assertNotContains(messages(steve), "this is where everybody is");
        day(3);
        var messages = messages(steve);
        assertContains(messages, "Day 3 - this is where everybody is:");
        assertContains(messages, "Steve Bears at " + steve.getLocation().getBlockX() + " " + steve.getLocation().getBlockY()
                + " " + steve.getLocation().getBlockZ());
        // where he left the game
        assertContains(messages, "Alex Foxes at " + position.getBlockX() + " " + position.getBlockY() + " "
                + position.getBlockZ());
        day(4);
        assertContains(messages(steve), "Day 4 - this is where everybody is:");
    }

    // the villager of players who leave the server

    private List<Villager> npcs() {
        return new ArrayList<>(game.getWorld().getEntitiesByClass(Villager.class));
    }

    @SuppressWarnings("removal")
    private boolean attack(PlayerMock attacker, Entity victim) {
        var event = new EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(attacker).withDirectEntity(attacker)
                        .build(), 5);
        server.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    @Test
    void aVillagerStandsInForPlayersWhoLeaveTheServer() {
        startGame();
        steve.setHealth(12);
        var position = steve.getLocation();
        steve.disconnect();

        assertEquals(1, npcs().size());
        Villager npc = npcs().getFirst();
        assertEquals("Steve (offline)", PlainTextComponentSerializer.plainText().serialize(npc.customName()));
        assertEquals(position.getBlockX(), npc.getLocation().getBlockX());
        assertEquals(12, npc.getHealth());
        assertFalse(npc.hasAI());
        assertTrue(attack(admin, npc), "enemies can hurt it");

        assertFalse(fall(npc), "only players");

        seconds(61);
        assertTrue(npcs().isEmpty(), "gone after offline-npc-seconds");
        assertTrue(participant(steve).isAlive());
    }

    @Test
    void killingTheVillagerKillsThePlayer() {
        startGame();
        steve.getInventory().addItem(new ItemStack(Material.DIAMOND, 3));
        steve.disconnect();
        Villager npc = npcs().getFirst();
        messages(admin);

        npc.setKiller(admin);
        npc.setHealth(0);
        assertFalse(participant(steve).isAlive());
        assertEquals(1, participant(admin).getKills());
        assertContains(messages(admin), "Steve has left the server and was killed by Admin!");
        assertTrue(game.getWorld().getEntitiesByClass(Item.class).stream()
                .anyMatch(item -> item.getItemStack().getType() == Material.DIAMOND && item.getItemStack().getAmount() == 3),
                "his items drop");
        assertNotNull(game.getJoinDenial(steve.getUniqueId()), "he is out when he comes back");
    }

    @Test
    void teamMatesAndOutsidersCantHurtTheVillager() {
        PlayerMock bob = addPlayer("Bob");
        execute(bob, "sg join varo1");
        execute(bob, "sg team join Bears");
        startGame();
        steve.disconnect();
        Villager npc = npcs().getFirst();
        assertFalse(attack(bob, npc), "same team");
        assertFalse(attack(addPlayer("Outsider"), npc), "not in the game");

        config().set("varo.peaceful-days", 1);
        assertFalse(attack(admin, npc), "no PvP");
    }

    @Test
    void theDamageOfTheVillagerStaysWhenThePlayerComesBack() {
        startGame();
        steve.disconnect();
        Villager npc = npcs().getFirst();
        npc.setHealth(6);

        steve.reconnect();
        execute(steve, "sg join varo1");
        assertSame(game, plugin.getUser(steve).getGame());
        assertTrue(npcs().isEmpty());
        assertEquals(6, steve.getHealth());
    }

    @Test
    void noVillagerIfTurnedOffOrLeavingByCommand() {
        startGame();
        execute(alex, "sg leave");
        assertTrue(npcs().isEmpty(), "only for players leaving the server");

        config().set("varo.offline-npc-seconds", 0);
        steve.disconnect();
        assertTrue(npcs().isEmpty());
    }

    // backups

    @Test
    void backupsContainTheWorldAndTheGame(@org.junit.jupiter.api.io.TempDir File temp) throws IOException {
        File worldFolder = new File(temp, "world");
        assertTrue(new File(worldFolder, "region").mkdirs());
        Files.writeString(new File(worldFolder, "level.dat").toPath(), "level");
        Files.writeString(new File(worldFolder, "region/r.0.0.mca").toPath(), "region");
        Files.writeString(new File(worldFolder, "session.lock").toPath(), "locked by the server");
        File saved = new File(temp, "varo1.yml");
        Files.writeString(saved.toPath(), "state: RUNNING");
        File target = new File(temp, "2026-10-10.zip");

        Backups.zip(worldFolder, saved, target);

        try (ZipFile zip = new ZipFile(target)) {
            List<String> names = zip.stream().map(ZipEntry::getName).sorted().toList();
            assertEquals(List.of("game.yml", "world/level.dat", "world/region/r.0.0.mca"), names);
            assertEquals("state: RUNNING", new String(zip.getInputStream(zip.getEntry("game.yml")).readAllBytes()));
        }
        assertFalse(new File(temp, "2026-10-10.zip.tmp").exists());
    }

    @Test
    void onlyTheNewestBackupsAreKept(@org.junit.jupiter.api.io.TempDir File temp) throws IOException {
        for (int day = 1; day <= 5; day++) {
            File backup = new File(temp, "2026-10-0" + day + ".zip");
            Files.writeString(backup.toPath(), "backup");
            assertTrue(backup.setLastModified(1_700_000_000_000L + day * 86_400_000L));
        }
        Files.writeString(new File(temp, "notes.txt").toPath(), "not a backup");

        Backups.prune(temp, 2);

        assertEquals(List.of("2026-10-04.zip", "2026-10-05.zip", "notes.txt"),
                java.util.Arrays.stream(temp.list()).sorted().toList());
        Backups.prune(temp, 7);
        assertEquals(3, temp.list().length);
    }

    @Test
    void backupsAreUnpackedAgain(@org.junit.jupiter.api.io.TempDir File temp) throws IOException {
        File worldFolder = new File(temp, "world");
        assertTrue(new File(worldFolder, "region").mkdirs());
        Files.writeString(new File(worldFolder, "region/r.0.0.mca").toPath(), "region");
        File saved = new File(temp, "varo1.yml");
        Files.writeString(saved.toPath(), "state: RUNNING");
        File backup = new File(temp, "2026-10-10.zip");
        Backups.zip(worldFolder, saved, backup);

        File restoredWorld = new File(temp, "restored");
        File restoredGame = new File(temp, "games/varo1.yml");
        Backups.unzip(backup, restoredWorld, restoredGame);
        assertEquals("region", Files.readString(new File(restoredWorld, "region/r.0.0.mca").toPath()));
        assertEquals("state: RUNNING", Files.readString(restoredGame.toPath()));
        assertEquals(List.of(backup), Backups.list(temp));
        assertTrue(Backups.list(new File(temp, "nothing")).isEmpty());
    }

    @Test
    void foreignZipFilesCantWriteOutsideOfTheWorld(@org.junit.jupiter.api.io.TempDir File temp) throws IOException {
        File backup = new File(temp, "evil.zip");
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(backup.toPath()))) {
            zip.putNextEntry(new ZipEntry("world/../../evil.txt"));
            zip.write("evil".getBytes());
            zip.closeEntry();
        }
        File worldFolder = new File(temp, "a/world");
        assertThrows(IOException.class, () -> Backups.unzip(backup, worldFolder, new File(temp, "game.yml")));
        assertFalse(new File(temp, "evil.txt").exists());
    }

    /**
     * runs the command and waits for the backup it writes
     */
    private void backup() {
        long start = System.currentTimeMillis();
        execute(admin, "sg game varo1 backup");
        // waitAsyncTasksFinished sometimes returns before the zip task has started (MockBukkit 4.118)
        long timeout = start + 10_000;
        while (VaroGame.getBackups(game.getArena()).stream().noneMatch(file -> file.lastModified() >= start)
                && System.currentTimeMillis() < timeout) {
            server.getScheduler().waitAsyncTasksFinished();
        }
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performOneTick();
    }

    @Test
    void adminsMakeBackups() {
        execute(admin, "sg game varo1 backups");
        assertContains(messages(admin), "There are no backups of varo1 yet");
        config().set("varo.backups", 0);
        startGame();
        backup();
        List<File> backups = VaroGame.getBackups(game.getArena());
        assertEquals(1, backups.size(), "also with backups: 0");
        String name = backups.getFirst().getName().replace(".zip", "");
        List<String> messages = messages(admin);
        assertContains(messages, "Saving the world of varo1 for a backup...");
        assertContains(messages, "Backup " + name + " of varo1 has been made.");

        execute(admin, "sg game varo1 backups");
        messages = messages(admin);
        assertContains(messages, "Backups of varo1");
        assertContains(messages, name + " - ");
        execute(steve, "sg game varo1 backup");
        assertEquals(1, VaroGame.getBackups(game.getArena()).size(), "only admins");
    }

    @Test
    void aBackupBringsTheGameBack() throws IOException {
        config().set("varo.backups", 0);
        startGame();
        File worldFolder = game.getGameWorld().getWorldFolder();
        Files.createDirectories(worldFolder.toPath());
        Files.writeString(new File(worldFolder, "saved.txt").toPath(), "in the backup");
        backup();
        String name = VaroGame.getBackups(game.getArena()).getFirst().getName().replace(".zip", "");
        Files.writeString(new File(worldFolder, "later.txt").toPath(), "after the backup");
        execute(admin, "sg game varo1 strike Steve griefing");
        assertEquals(1, game.getParticipant(steve.getUniqueId()).getStrikes().size());
        messages(admin);

        execute(admin, "sg game varo1 restore nothing");
        assertContains(messages(admin), "There is no backup nothing.");
        execute(admin, "sg game varo1 restore " + name);
        assertContains(messages(admin), "everything since then is lost");
        assertSame(game, plugin.getGameManager().getGame(game.getArena()), "only with --confirm");

        execute(admin, "sg game varo1 restore " + name + " --confirm");
        List<String> messages = messages(admin);
        assertContains(messages, "Admin has restored the game from the backup " + name + ".");
        assertContains(messages, "The backup " + name + " of varo1 has been restored.");
        VaroGame restored = (VaroGame) plugin.getGameManager().getGame(game.getArena());
        assertNotSame(game, restored);
        assertEquals(GameState.RUNNING, restored.getState());
        assertTrue(restored.getParticipant(steve.getUniqueId()).getStrikes().isEmpty(), "the game of the backup");
        assertSame(restored, plugin.getUser(steve).getGame(), "the players are back in the game");
        assertSame(restored, plugin.getUser(admin).getGame());
        assertTrue(new File(worldFolder, "saved.txt").isFile());
        assertFalse(new File(worldFolder, "later.txt").exists(), "the world of the backup");
    }

    // play hours

    /**
     * the time of the server (2026-10-12 is a monday)
     */
    private void time(LocalDateTime time) {
        game.setClock(Clock.fixed(time.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
    }

    @Test
    void theGameIsLeftWhenThePlayHoursEnd() {
        config().set("varo.play-hours", List.of("18:00-20:00"));
        time(LocalDateTime.of(2026, 10, 12, 19, 59, 30));
        startGame();
        assertContains(messages(steve), "The Varo can only be played at these times: 18:00-20:00");
        assertNotContains(messages(admin), "outside of its play hours");
        assertEquals("", Placeholders.value(plugin, steve, "opens"));
        assertEquals(30, game.getSessionTime(participant(steve)));
        seconds(1);
        assertContains(messages(steve), "Your play time ends in 00:30");
        execute(steve, "sg time");
        assertContains(messages(steve), "The Varo can only be played at these times: 18:00-20:00");

        time(LocalDateTime.of(2026, 10, 12, 20, 0));
        seconds(1);
        assertNull(plugin.getUser(steve).getGame());
        List<String> messages = messages(steve);
        assertContains(messages, "The play hours are over for now.");
        assertContains(messages, "The Varo can't be played right now.");
        assertContains(messages, "You can play again in 22h 0min");
        assertNotContains(messages, "has used up the play time");
        assertTrue(game.getRemaining(participant(steve)) > 0, "the play time of the day isn't used up");

        execute(steve, "sg join varo1");
        assertNull(plugin.getUser(steve).getGame());
        assertContains(messages(steve), "The Varo can't be played right now.");

        time(LocalDateTime.of(2026, 10, 13, 18, 0));
        assertNull(game.getJoinDenial(steve.getUniqueId()));
        execute(steve, "sg join varo1");
        assertSame(game, plugin.getUser(steve).getGame());
    }

    @Test
    void startingOutsideOfThePlayHoursShowsWhenTheVaroOpens() {
        config().set("varo.play-hours", List.of("18:00-20:00"));
        time(LocalDateTime.of(2026, 10, 12, 21, 0));
        assertEquals("Tue 18:00", Placeholders.value(plugin, null, "game_opens_varo1"), "also in the lobby");
        String lobby = Placeholders.value(plugin, null, "game_state_varo1");
        assertTrue(lobby.contains("Lobby"), "players can still join: " + lobby);
        startGame();
        List<String> messages = messages(admin);
        assertContains(messages, "The Varo in varo1 is outside of its play hours");
        assertContains(messages, "can play again Tue 18:00");
        assertContains(messages(steve), "The play hours begin Tue 18:00 - nobody can play until then.");
        seconds(1);
        assertNull(plugin.getUser(steve).getGame());

        assertEquals("Tue 18:00", Placeholders.value(plugin, steve, "opens"));
        assertEquals("Tue 18:00", Placeholders.value(plugin, null, "game_opens_varo1"));
        String state = Placeholders.value(plugin, null, "game_state_varo1");
        assertTrue(state.contains("Opens Tue 18:00"), "signs, NPCs and menus: " + state);
        ServerStatus status = ServerStatus.of(game);
        assertEquals(status, ServerStatus.parse(status.toMotd()), "lobby servers get it too");

        time(LocalDateTime.of(2026, 10, 13, 18, 0));
        assertNull(game.getOpening());
        assertEquals("", Placeholders.value(plugin, null, "game_opens_varo1"));
    }

    @Test
    void playHoursOfTheDaysOfTheWeek() {
        config().set("varo.play-hours", List.of("sat,sun 14:00-22:00"));
        time(LocalDateTime.of(2026, 10, 10, 15, 0));
        startGame();
        seconds(1);
        assertSame(game, plugin.getUser(steve).getGame());

        time(LocalDateTime.of(2026, 10, 12, 15, 0));
        seconds(1);
        assertNull(plugin.getUser(steve).getGame(), "not on a monday");
        String denial = PlainTextComponentSerializer.plainText().serialize(game.getJoinDenial(steve.getUniqueId()));
        assertTrue(denial.contains("You can play again in 119h 0min"), denial);
    }

    @Test
    void noPlayTimeLeftWaitsForThePlayHoursOfTheNextDay() {
        config().set("varo.play-hours", List.of("18:00-20:00"));
        config().set("varo.play-time", 1);
        time(LocalDateTime.of(2026, 10, 12, 18, 0));
        startGame();
        seconds(60);
        assertNull(plugin.getUser(steve).getGame());
        assertContains(messages(steve), "Your play time for today is over");
        String denial = PlainTextComponentSerializer.plainText().serialize(game.getJoinDenial(steve.getUniqueId()));
        assertTrue(denial.contains("Your play time for today is over"), denial);
        assertTrue(denial.contains("You can play again in 24h 0min"), "not at midnight: " + denial);
    }

    @Test
    void enemiesNearbyKeepThePlayerInTheGameAfterThePlayHours() {
        config().set("varo.play-hours", List.of("18:00-20:00"));
        config().set("varo.combat-radius", 20);
        time(LocalDateTime.of(2026, 10, 12, 19, 0));
        startGame();
        steve.teleport(admin.getLocation());
        time(LocalDateTime.of(2026, 10, 12, 20, 0));
        seconds(2);
        assertSame(game, plugin.getUser(steve).getGame(), "no escape from a fight");
    }

}
