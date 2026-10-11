package com.christian34.survivalgames.game;

import com.christian34.survivalgames.PluginTestBase;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Varo in an arena: teams, play time per day, the inventory in and outside the game, eliminations, the winner, the
 * reset and the saved game.
 *
 * @author Christian34
 */
class VaroFlowTest extends PluginTestBase {
    private VaroGame game;
    private PlayerMock admin;
    private PlayerMock steve;

    @BeforeEach
    void setUpGame() {
        plugin.getStats().setExecutor(Runnable::run);
        config().set("varo.start-countdown", 3);
        // both players stand next to each other on their spawn points
        config().set("varo.combat-radius", 0);
        config().set("varo.join-protection", 0);
        game = (VaroGame) createGame("varo1", GameType.VARO, 3);
        admin = addAdmin("Admin");
        steve = addPlayer("Steve");
        execute(admin, "sg join varo1");
        execute(steve, "sg join varo1");
        execute(admin, "sg team create Wolves");
        execute(steve, "sg team create Bears");
        messages(admin);
        messages(steve);
    }

    private Participant participant(PlayerMock player) {
        return game.getParticipant(player.getUniqueId());
    }

    /**
     * starts the game and runs the countdown (the play time hasn't started running yet)
     */
    private void startGame() {
        execute(admin, "sg game varo1 start");
        assertEquals(GameState.STARTING, game.getState());
        for (int tick = 0; tick < 20 * 5 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());
        messages(admin);
        messages(steve);
    }

    @Test
    void countdownStartsTheGame() {
        execute(admin, "sg game varo1 start");
        assertEquals(GameState.STARTING, game.getState());
        assertEquals(64, steve.getLocation().getBlockY(), "on the spawn point");
        assertTrue(steve.getInventory().isEmpty(), "no lobby items");

        seconds(5);

        assertEquals(GameState.RUNNING, game.getState());
        assertContains(messages(steve), "Varo has started!");
        assertEquals(GameMode.SURVIVAL, steve.getGameMode());
        assertEquals(1, game.getDay());
    }

    @Test
    void startNeedsEnoughPlayers() {
        // players with a team take part while they are away, the others leave the game
        execute(steve, "sg team leave");
        execute(steve, "sg leave");
        execute(admin, "sg game varo1 start");
        assertEquals(GameState.LOBBY, game.getState());
        assertContains(messages(admin), "The game can't start: only 1 of 2 players are there.");
        execute(admin, "sg game varo1 start --force");
        assertEquals(GameState.STARTING, game.getState());
    }

    @Test
    void deathEliminatesAndTheLastTeamWins() {
        startGame();
        game.eliminate(participant(steve), participant(admin));

        assertFalse(participant(steve).isAlive());
        assertEquals(1, participant(admin).getKills());
        assertEquals(GameState.ENDED, game.getState());
        assertEquals("Wolves", game.getWinner());
        java.util.List<String> messages = messages(admin);
        assertContains(messages, "Team Wolves has won in varo1!");
        assertContains(messages, "Achievement unlocked! Survivor - Win a Varo alive");
        assertContains(messages, "Achievement unlocked! Spotless - Win a Varo without a strike");
        assertNotContains(messages(steve), "Survivor");
    }

    @Test
    void theGameGoesOnWhileATeamHasPlayersLeft() {
        PlayerMock alex = addPlayer("Alex");
        execute(alex, "sg join varo1");
        execute(alex, "sg team join Bears");
        startGame();

        game.eliminate(participant(steve), null);
        assertEquals(GameState.RUNNING, game.getState());
        assertEquals(2, game.getAliveTeams().size());
        assertContains(messages(alex), "Steve (Bears) is out of the game! 2 teams left.");

        game.eliminate(participant(alex), null);
        assertEquals(GameState.ENDED, game.getState());
    }

    @Test
    void theInventoryOutsideTheGameStaysSeparate() {
        execute(steve, "sg leave");
        steve.getInventory().addItem(new ItemStack(Material.DIAMOND, 3));
        execute(steve, "sg join varo1");
        startGame();
        steve.getInventory().addItem(new ItemStack(Material.DIRT, 10));
        Location inGame = steve.getLocation();

        execute(steve, "sg leave");
        assertTrue(steve.getInventory().contains(Material.DIAMOND, 3), "the own inventory is back");
        assertFalse(steve.getInventory().contains(Material.DIRT));
        assertEquals(world, steve.getWorld());

        execute(steve, "sg join varo1");
        assertTrue(steve.getInventory().contains(Material.DIRT, 10), "the inventory of the game is back");
        assertFalse(steve.getInventory().contains(Material.DIAMOND));
        assertEquals(inGame.getBlockX(), steve.getLocation().getBlockX(), "where he left the game");
    }

    @Test
    void playTimeOnlyRunsInTheGame() {
        config().set("varo.play-time", 1);
        startGame();
        seconds(10);
        assertEquals(50, game.getRemaining(participant(steve)));
        execute(steve, "sg leave");
        seconds(10);
        assertEquals(50, game.getRemaining(participant(steve)));
    }

    @Test
    void playTimeRunsOut() {
        config().set("varo.play-time", 1);
        startGame();
        assertEquals(60, game.getRemaining(participant(steve)));

        seconds(30);
        assertEquals(30, game.getRemaining(participant(steve)));
        assertContains(messages(steve), "Your play time ends in 00:30");

        seconds(30);
        assertNull(plugin.getUser(steve).getGame(), "out of the game for today");
        assertTrue(steve.isOnline(), "only the game is left on a server with several games");
        assertContains(messages(steve), "Your play time for today is over");
        assertNotNull(game.getJoinDenial(steve.getUniqueId()));
        execute(steve, "sg join varo1");
        assertNull(plugin.getUser(steve).getGame());
        assertTrue(participant(steve).isAlive(), "still in the game");
    }

    @Test
    void enemiesNearbyKeepThePlayerInTheGame() {
        config().set("varo.play-time", 1);
        config().set("varo.combat-radius", 20);
        startGame();
        steve.teleport(admin.getLocation());
        seconds(65);
        assertSame(game, plugin.getUser(steve).getGame(), "no escape from a fight");
        execute(steve, "sg leave");
        assertSame(game, plugin.getUser(steve).getGame());
        assertContains(messages(steve), "An enemy is nearby");

        steve.teleport(new Location(game.getWorld(), 500, 70, 500));
        seconds(2);
        assertNull(plugin.getUser(steve).getGame());
    }

    @Test
    void newDayGivesTheFullPlayTime() {
        config().set("varo.play-time", 1);
        startGame();
        seconds(61);
        assertNull(plugin.getUser(steve).getGame());

        game.setClock(Clock.offset(Clock.systemDefaultZone(), Duration.ofDays(1)));
        assertEquals(2, game.getDay());
        assertNull(game.getJoinDenial(steve.getUniqueId()));
        assertEquals(60, game.getRemaining(participant(steve)));
    }

    @Test
    void worldBorderShrinksEveryDay() {
        game.getArena().setBorderSize(1000);
        config().set("varo.world-border.shrink-per-day", 100);
        startGame();
        assertEquals(1000, game.getWorld().getWorldBorder().getSize());

        game.setClock(Clock.offset(Clock.systemDefaultZone(), Duration.ofDays(2)));
        seconds(1);

        var messages = messages(steve);
        assertContains(messages, "Day 3 has begun");
        assertContains(messages, "The world border shrinks to 800 blocks");
    }

    @Test
    void playTimeIsSavedPerDay() {
        Participant participant = new Participant(UUID.randomUUID(), "Test");
        participant.setPlayed("2026-10-09", 100);
        assertEquals(100, participant.getPlayed("2026-10-09"));
        assertEquals(0, participant.getPlayed("2026-10-10"));
    }

    @Test
    void timeFormat() {
        assertEquals("15:00", Game.formatTime(900));
        assertEquals("00:05", Game.formatTime(5));
        assertEquals("60:00", Game.formatTime(3600));
    }

    @Test
    void eliminatedPlayersWatch() {
        PlayerMock alex = addPlayer("Alex");
        execute(alex, "sg join varo1");
        execute(alex, "sg team join Bears");
        startGame();

        steve.setHealth(0);
        steve.respawn();
        server.getScheduler().performOneTick();
        assertFalse(participant(steve).isAlive());
        assertEquals(GameMode.SPECTATOR, steve.getGameMode());
        assertTrue(plugin.getUser(steve).isSpectator());
    }

    @Test
    void reviveBringsThePlayerBack() {
        PlayerMock alex = addPlayer("Alex");
        execute(alex, "sg join varo1");
        execute(alex, "sg team join Bears");
        startGame();
        game.eliminate(participant(steve), null);

        execute(admin, "sg game varo1 revive Steve");
        assertTrue(participant(steve).isAlive());
        assertContains(messages(alex), "Steve has been revived");
    }

    @Test
    void resetGoesBackToTheLobby() {
        startGame();
        game.eliminate(participant(steve), participant(admin));

        execute(admin, "sg game varo1 reset");
        assertSame(game, plugin.getGameManager().getGame(game.getArena()), "needs --confirm");
        execute(admin, "sg game varo1 reset --confirm");

        VaroGame next = (VaroGame) plugin.getGameManager().getGame(game.getArena());
        assertNotSame(game, next);
        assertEquals(GameState.LOBBY, next.getState());
        assertTrue(next.getParticipant(steve.getUniqueId()).isAlive());
        assertEquals(0, next.getParticipant(admin.getUniqueId()).getKills());
        assertEquals(2, next.getTeams().getTeams().size(), "the teams stay");
        assertSame(next, plugin.getUser(steve).getGame(), "the players are in the lobby again");

        execute(admin, "sg game varo1 reset --teams --confirm");
        assertTrue(plugin.getGameManager().getGame(game.getArena()).getTeams().getTeams().isEmpty());
    }

    @Test
    void theGameIsSavedAndLoadedAgain() {
        startGame();
        steve.getInventory().addItem(new ItemStack(Material.DIRT, 7));
        plugin.getGameManager().stopGame(game, false);
        assertEquals(world, steve.getWorld());

        VaroGame loaded = (VaroGame) plugin.getGameManager().createGame(game.getArena());
        assertEquals(GameState.RUNNING, loaded.getState());
        assertEquals(2, loaded.getTeams().getTeams().size());
        execute(steve, "sg join varo1");
        assertSame(loaded, plugin.getUser(steve).getGame());
        assertTrue(steve.getInventory().contains(Material.DIRT, 7));
    }

    @Test
    void savedVarosComeBackAfterARestart() {
        startGame();
        plugin.getGameManager().stopAll();
        plugin.getGameManager().createAutoGames();
        assertNotNull(plugin.getGameManager().getGame(game.getArena()), "no auto-game, but a saved Varo");
    }

    @Test
    void teamMembersDontHurtEachOther() {
        PlayerMock alex = addPlayer("Alex");
        execute(alex, "sg join varo1");
        execute(alex, "sg team join Bears");
        startGame();

        assertFalse(attack(alex, steve));
        assertTrue(attack(admin, steve));
        config().set("varo.friendly-fire", true);
        assertTrue(attack(alex, steve));
    }

    @Test
    void noDamageDuringTheCountdown() {
        assertFalse(fall(steve), "lobby");
        execute(admin, "sg game varo1 start");
        assertFalse(fall(steve), "countdown");
    }

    @Test
    void strikesHaveTheirConsequences() {
        startGame();
        steve.getInventory().addItem(new ItemStack(Material.DIRT, 5));
        Location position = steve.getLocation();

        execute(admin, "sg game varo1 strike Steve camping at the spawn");
        var messages = messages(steve);
        assertContains(messages, "Steve has got strike #1: camping at the spawn");
        assertContains(messages, "The position of Steve is " + position.getBlockX() + " " + position.getBlockY() + " "
                + position.getBlockZ());

        execute(admin, "sg game varo1 strike Steve");
        assertContains(messages(steve), "Steve has got strike #2: no reason given");
        assertFalse(steve.getInventory().contains(Material.DIRT), "the inventory has been cleared");

        execute(steve, "sg strikes");
        messages = messages(steve);
        assertContains(messages, "Strikes of Steve (2):");
        assertContains(messages, "#1 camping at the spawn - Admin");

        execute(admin, "sg game varo1 strike Steve");
        assertFalse(participant(steve).isAlive(), "out after the third strike");
        assertEquals(GameState.ENDED, game.getState());
        assertTrue(plugin.getUser(steve).isSpectator());
    }

    @Test
    void strikesOfPlayersOutsideTheGameUseTheirSavedState() {
        startGame();
        steve.getInventory().addItem(new ItemStack(Material.DIRT, 5));
        Location position = steve.getLocation();
        execute(steve, "sg leave");
        messages(steve);

        execute(admin, "sg game varo1 strike Steve");
        // he still gets the announcement as a participant
        assertContains(messages(steve), "The position of Steve is " + position.getBlockX());
        execute(admin, "sg game varo1 strike Steve");
        execute(steve, "sg join varo1");
        assertFalse(steve.getInventory().contains(Material.DIRT), "the inventory in the game has been cleared");
    }

    @Test
    void strikesCanBeRemovedAndAreSaved() {
        startGame();
        execute(admin, "sg game varo1 strike Steve one");
        execute(admin, "sg game varo1 strike Steve two");
        execute(admin, "sg game varo1 unstrike Steve");
        assertContains(messages(admin), "The last strike of Steve has been removed (1 left).");
        execute(admin, "sg game varo1 strikes");
        assertContains(messages(admin), "Steve: 1");

        plugin.getGameManager().stopGame(game, false);
        VaroGame loaded = (VaroGame) plugin.getGameManager().createGame(game.getArena());
        var strikes = loaded.getParticipant(steve.getUniqueId()).getStrikes();
        assertEquals(1, strikes.size());
        assertEquals("one", strikes.getFirst().reason());
        assertEquals("Admin", strikes.getFirst().by());
    }

    @Test
    void strikesOnlyInARunningVaro() {
        execute(admin, "sg game varo1 strike Steve");
        assertContains(messages(admin), "The game is not running.");
        assertTrue(participant(steve).getStrikes().isEmpty());
    }

    @Test
    void infoShowsTheGame() {
        startGame();
        execute(steve, "sg info");
        var messages = messages(steve);
        assertContains(messages, "State: Running");
        assertContains(messages, "Teams alive: 2/2");
        execute(steve, "sg time");
        assertContains(messages(steve), "more today");
    }

}
