package com.christian34.survivalgames.game;

import com.christian34.survivalgames.PluginTestBase;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A round of Survival Games: lobby countdown, spawn points, grace period, eliminations, the winner, the deathmatch and
 * the next round.
 *
 * @author Christian34
 */
class SurvivalGamesFlowTest extends PluginTestBase {
    private SurvivalGame game;
    private PlayerMock steve;
    private PlayerMock alex;

    @BeforeEach
    void setUpGame() {
        config().set("survival-games.lobby-countdown", 10);
        config().set("survival-games.full-countdown", 5);
        config().set("survival-games.start-countdown", 3);
        config().set("survival-games.grace-period", 5);
        config().set("survival-games.ending-seconds", 3);
        game = (SurvivalGame) createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        game.getArena().setAutoGame(true);
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
    }

    private void join(PlayerMock player) {
        execute(player, "sg join arena1");
        assertSame(game, plugin.getUser(player).getGame());
    }

    /**
     * both players join, the countdowns run and the game begins
     */
    private void begin() {
        join(steve);
        join(alex);
        for (int tick = 0; tick < 20 * 30 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());
        messages(steve);
        messages(alex);
    }

    @Test
    void joiningSavesTheInventoryAndLeavingBringsItBack() {
        steve.getInventory().addItem(new ItemStack(Material.DIAMOND, 5));
        Location before = steve.getLocation();
        join(steve);

        assertEquals(game.getWorld(), steve.getWorld(), "in the lobby of the arena");
        assertEquals(100, steve.getLocation().getBlockY());
        assertEquals(GameMode.ADVENTURE, steve.getGameMode());
        assertFalse(steve.getInventory().contains(Material.DIAMOND));
        assertTrue(steve.getInventory().contains(Material.RED_BED), "leave item");

        execute(steve, "sg leave");
        assertNull(plugin.getUser(steve).getGame());
        assertTrue(steve.getInventory().contains(Material.DIAMOND, 5));
        assertEquals(before.getWorld(), steve.getWorld());
        assertContains(messages(steve), "You have left the game");
    }

    @Test
    void lobbyCountdownStartsWithEnoughPlayers() {
        join(steve);
        assertEquals(-1, game.getCountdown());
        join(alex);
        assertEquals(10, game.getCountdown());

        execute(alex, "sg leave");
        assertEquals(-1, game.getCountdown());
        assertContains(messages(steve), "the countdown has been cancelled");
    }

    @Test
    void fullLobbyShortensTheCountdown() {
        game.getArena().setPlayers(2, 2);
        join(steve);
        join(alex);
        assertEquals(5, game.getCountdown());
        assertNotNull(game.getJoinDenial(addPlayer("Other").getUniqueId()), "full");
    }

    @Test
    void playersStandOnTheirSpawnPointsUntilTheGameBegins() {
        join(steve);
        join(alex);
        for (int tick = 0; tick < 20 * 15 && game.getState() == GameState.LOBBY; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.STARTING, game.getState());
        assertEquals(64, steve.getLocation().getBlockY(), "on the spawn point");
        var toCenter = game.getArena().getCenter(game.getWorld()).toVector().subtract(steve.getLocation().toVector())
                .setY(0).normalize();
        assertTrue(steve.getLocation().getDirection().dot(toCenter) > 0.99, "looking at the center");
        assertEquals(0, steve.getLocation().getPitch(), 0.001, "looking straight");
        assertEquals(GameMode.SURVIVAL, steve.getGameMode());
        assertTrue(steve.getInventory().isEmpty(), "no lobby items");
        assertFalse(fall(steve), "no damage during the countdown");

        seconds(3);
        assertEquals(GameState.RUNNING, game.getState());
        assertContains(messages(steve), "No PvP for 5 seconds");
    }

    @Test
    void gracePeriodProtectsThePlayers() {
        begin();
        assertTrue(game.isGracePeriod());
        assertFalse(attack(steve, alex));
        seconds(5);
        assertFalse(game.isGracePeriod());
        assertContains(messages(steve), "The grace period is over");
        assertTrue(attack(steve, alex));
    }

    @Test
    void theLastPlayerWinsAndTheNextRoundStarts() {
        begin();
        alex.setHealth(0);
        alex.respawn();
        server.getScheduler().performOneTick();

        assertEquals(GameState.ENDED, game.getState());
        assertContains(messages(steve), "Steve has won in arena1!");
        assertEquals(GameMode.SPECTATOR, alex.getGameMode(), "the eliminated player watches");

        seconds(3);
        assertNull(plugin.getUser(steve).getGame(), "everybody left the arena");
        assertEquals(world, steve.getWorld());
        Game next = plugin.getGameManager().getGame(game.getArena());
        assertNotNull(next, "auto-game: the next round");
        assertNotSame(game, next);
        assertEquals(GameState.LOBBY, next.getState());
        assertContains(messages(steve), "Play again");
        assertNull(game.getGameWorld().getWorld(), "the world of the round has been deleted");
    }

    @Test
    void leavingTheRunningGameEliminates() {
        begin();
        execute(alex, "sg leave");
        assertEquals(GameState.ENDED, game.getState());
        assertFalse(game.getParticipant(alex.getUniqueId()).isAlive());
    }

    @Test
    void quittingTheServerEliminates() {
        begin();
        alex.disconnect();
        assertEquals(GameState.ENDED, game.getState());
    }

    @Test
    void deathmatchTeleportsToTheSpawnPoints() {
        config().set("survival-games.deathmatch.minutes", 1);
        config().set("survival-games.deathmatch.warning", 10);
        config().set("survival-games.deathmatch.border-size", 50);
        begin();
        steve.teleport(new Location(game.getWorld(), 200, 64, 200));
        seconds(60);
        assertContains(messages(steve), "The deathmatch starts in 10 seconds");
        seconds(10);
        assertTrue(game.isDeathmatch());
        assertEquals(64, steve.getLocation().getBlockY());
        assertTrue(Math.abs(steve.getLocation().getBlockX()) <= 21);
        double border = game.getWorld().getWorldBorder().getSize();
        assertTrue(border > 40 && border <= 50, "shrinks from 50: " + border);
    }

    @Test
    void chestsGetLootWhenTheyAreOpened() {
        begin();
        Block block = game.getWorld().getBlockAt(5, 64, 5);
        block.setType(Material.CHEST);
        var inventory = ((Chest) block.getState()).getInventory();
        assertTrue(inventory.isEmpty());

        game.fillContainer(inventory);
        int items = (int) java.util.Arrays.stream(inventory.getContents()).filter(item -> item != null).count();
        assertTrue(items >= 3 && items <= 6, "between items.min and items.max: " + items);

        inventory.clear();
        game.fillContainer(inventory);
        assertTrue(inventory.isEmpty(), "only once until the refill");
    }

    @Test
    void spectatorsWatchARunningGame() {
        begin();
        PlayerMock other = addPlayer("Other");
        // too late to play: watching
        execute(other, "sg join arena1");
        assertTrue(plugin.getUser(other).isSpectator());
        assertEquals(GameMode.SPECTATOR, other.getGameMode());
        assertFalse(steve.canSee(other), "players don't see spectators");
        assertTrue(other.canSee(steve));
    }

    @Test
    void playersOutsideTheGameDontSeeIt() {
        PlayerMock other = addPlayer("Other");
        join(steve);
        assertFalse(other.canSee(steve));
        assertFalse(steve.canSee(other));
        execute(steve, "sg leave");
        assertTrue(other.canSee(steve));
    }

    @Test
    void stopKeepsNoPlayerInTheWorld() {
        begin();
        plugin.getGameManager().stopGame(game, false);
        assertNull(plugin.getUser(steve).getGame());
        assertEquals(world, steve.getWorld());
        assertNull(plugin.getGameManager().getGame(game.getArena()));
    }

    @Test
    void infoShowsTheGame() {
        begin();
        execute(steve, "sg info");
        var messages = messages(steve);
        assertContains(messages, "State: Running");
        assertContains(messages, "Players alive: 2/2");
    }

}
