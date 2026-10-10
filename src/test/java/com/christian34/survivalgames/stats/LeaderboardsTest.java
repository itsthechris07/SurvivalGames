package com.christian34.survivalgames.stats;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.lib.lang.I;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The best players in the world: holograms with the top 10 (/sg leaderboard) and signs with one rank ([sgtop]).
 *
 * @author Christian34
 */
class LeaderboardsTest extends PluginTestBase {
    private PlayerMock admin;
    private PlayerMock steve;
    private PlayerMock alex;

    @BeforeEach
    void setUpStats() {
        plugin.getStats().setExecutor(Runnable::run);
        config().set("survival-games.lobby-countdown", 1);
        config().set("survival-games.start-countdown", 1);
        config().set("survival-games.grace-period", 0);
        admin = addAdmin("Admin");
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
        admin.teleport(new Location(world, 8.5, 70, 8.5));
    }

    /**
     * Steve kills Alex and wins a round of Survival Games
     */
    private void playRound() {
        SurvivalGame game = (SurvivalGame) createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        for (int tick = 0; tick < 20 * 10 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());
        alex.setKiller(steve);
        alex.setHealth(0);
        assertEquals(GameState.ENDED, game.getState());
    }

    private Leaderboards leaderboards() {
        return plugin.getLeaderboards();
    }

    @Test
    void hologramsAreSavedAndRemoved() {
        execute(admin, "sg leaderboard create rating varo");
        assertContains(messages(admin), "The leaderboard of Rating (Varo) has been created.");
        seconds(1);
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "leaderboards.yml"));
        String id = leaderboards().getHolograms().getFirst().id();
        assertEquals("rating", saved.getString("holograms." + id + ".stat"));
        assertEquals("varo", saved.getString("holograms." + id + ".type"));
        assertEquals("world", saved.getString("holograms." + id + ".world"));
        assertEquals(71, saved.getDouble("holograms." + id + ".y"), "above the head");
        // MockBukkit can't spawn text displays: the chunk isn't loaded, so the hologram is spawned later
        assertNull(saved.getString("holograms." + id + ".entity"));

        execute(admin, "sg leaderboard list");
        assertContains(messages(admin), "Hologram Rating (Varo) - world 8 71 8");

        admin.teleport(new Location(world, 60, 70, 60));
        execute(admin, "sg leaderboard remove");
        assertContains(messages(admin), "There is no leaderboard within 5 blocks.");
        admin.teleport(new Location(world, 9, 70, 9));
        execute(admin, "sg leaderboard remove");
        assertContains(messages(admin), "The leaderboard has been removed.");
        assertTrue(leaderboards().getHolograms().isEmpty());
        execute(admin, "sg leaderboard list");
        assertContains(messages(admin), "There are no leaderboards yet");
    }

    @Test
    void invalidInput() {
        execute(admin, "sg leaderboard create points");
        assertContains(messages(admin), "Unknown statistic points");
        execute(admin, "sg leaderboard create wins skywars");
        assertContains(messages(admin), "Unknown game type skywars");
        execute(steve, "sg leaderboard create wins");
        assertContains(messages(steve), "You do not have permission");

        config().set("stats.enabled", false);
        plugin.getStats().reload();
        execute(admin, "sg leaderboard create wins");
        assertContains(messages(admin), "The statistics are turned off");
        assertTrue(leaderboards().getHolograms().isEmpty());
    }

    // signs

    private Block signBlock() {
        // signs are only updated in loaded chunks
        world.getChunkAt(0, 0).load();
        Block block = world.getBlockAt(3, 70, 3);
        block.setType(Material.OAK_SIGN);
        return block;
    }

    private SignChangeEvent write(PlayerMock player, Block block, String... lines) {
        SignChangeEvent event = new SignChangeEvent(block, player,
                new ArrayList<>(Arrays.stream(lines).map(line -> (Component) Component.text(line)).toList()), Side.FRONT);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void signShowsOneRank() {
        playRound();
        Block block = signBlock();
        SignChangeEvent event = write(admin, block, "[sgtop]", "kills", "1", "");
        assertContains(messages(admin), "The sign shows rank 1 of Kills (all games).");
        assertEquals("#1 Kills", I.plain(event.line(0)));
        assertEquals("all games", I.plain(event.line(3)));

        seconds(1);
        Sign sign = (Sign) block.getState();
        assertEquals("Steve", I.plain(sign.getSide(Side.FRONT).line(1)));
        assertEquals("1", I.plain(sign.getSide(Side.FRONT).line(2)));

        execute(admin, "sg leaderboard list");
        assertContains(messages(admin), "Sign #1 Kills (all games) - world 3 70 3");
    }

    @Test
    void signsWithoutAPlayerOnTheRank() {
        Block block = signBlock();
        // wins, rank 1 and all games if the lines are empty
        SignChangeEvent event = write(admin, block, "[SGTOP]", "", "", "");
        assertEquals("#1 Wins", I.plain(event.line(0)));
        assertEquals("---", I.plain(event.line(1)));
        assertEquals(1, leaderboards().getSigns().size());
    }

    @Test
    void invalidSigns() {
        Block block = signBlock();
        write(admin, block, "[sgtop]", "points", "", "");
        assertContains(messages(admin), "Unknown statistic points");
        write(admin, block, "[sgtop]", "wins", "11", "");
        assertContains(messages(admin), "Invalid rank 11 - 1 to 10.");
        write(admin, block, "[sgtop]", "wins", "1", "skywars");
        assertContains(messages(admin), "Unknown game type skywars");
        SignChangeEvent event = write(steve, block, "[sgtop]", "wins", "1", "");
        assertEquals("[sgtop]", I.plain(event.line(0)), "only admins");
        assertTrue(leaderboards().getSigns().isEmpty());
    }

    @Test
    void signsAreSavedAndRemovedWhileSneaking() {
        Block block = signBlock();
        write(admin, block, "[sgtop]", "deaths", "3", "varo");
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "leaderboards.yml"));
        var entry = saved.getMapList("signs").getFirst();
        assertEquals("deaths", entry.get("stat"));
        assertEquals("varo", entry.get("type"));
        assertEquals(3, entry.get("rank"));

        BlockBreakEvent event = new BlockBreakEvent(block, steve);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled(), "players can't break it");
        event = new BlockBreakEvent(block, admin);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled(), "not by accident");

        admin.setSneaking(true);
        event = new BlockBreakEvent(block, admin);
        server.getPluginManager().callEvent(event);
        assertFalse(event.isCancelled());
        assertTrue(leaderboards().getSigns().isEmpty());
    }

}
