package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.FakeEconomy;
import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.game.VaroGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bounties: players put money on a player alive, his killer gets it - otherwise it is paid back.
 *
 * @author Christian34
 */
class BountyTest extends PluginTestBase {
    private final FakeEconomy economy = new FakeEconomy();
    private SurvivalGame game;
    private PlayerMock steve;
    private PlayerMock alex;
    private PlayerMock bob;

    @BeforeEach
    void setUpGame() {
        plugin.getStats().setExecutor(Runnable::run);
        config().set("survival-games.lobby-countdown", 1);
        config().set("survival-games.start-countdown", 1);
        config().set("survival-games.grace-period", 0);
        config().set("survival-games.deathmatch.players", 0);
        plugin.getEconomySupport().setProvider(economy);
        game = (SurvivalGame) createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
        bob = addPlayer("Bob");
        economy.set(steve, 100);
        economy.set(bob, 30);
    }

    private void join() {
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        execute(bob, "sg join arena1");
    }

    private void begin() {
        join();
        for (int tick = 0; tick < 20 * 10 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());
        messages(steve);
        messages(alex);
        messages(bob);
    }

    private double total(PlayerMock target) {
        return plugin.getBounty().getTotal(game, target.getUniqueId());
    }

    @Test
    void theKillerGetsTheBounty() {
        begin();
        execute(steve, "sg bounty Alex 50");
        assertContains(messages(alex), "Steve has put a bounty of $50 on Alex! Total: $50");
        assertEquals(50, economy.balance(steve), "the money is taken right away");
        execute(bob, "sg bounty Alex 20");
        assertContains(messages(alex), "Bob has put a bounty of $20 on Alex! Total: $70");
        assertEquals(70, total(alex));

        alex.setKiller(bob);
        alex.setHealth(0);
        assertContains(messages(steve), "Bob has claimed the bounty of $70 on Alex!");
        assertContains(messages(bob), "Achievement unlocked! Bounty hunter");
        assertEquals(80, economy.balance(bob), "10 left + 70");
        assertEquals(50, economy.balance(steve));
        assertEquals(0, total(alex));
    }

    @Test
    void bountiesAreListed() {
        begin();
        execute(steve, "sg bounty");
        assertContains(messages(steve), "There are no bounties.");
        execute(steve, "sg bounty Alex 30");
        execute(steve, "sg bounty Bob 60");
        messages(alex);

        execute(alex, "sg bounty");
        var messages = messages(alex);
        assertContains(messages, "Bob: $60");
        assertContains(messages, "Alex: $30");
        assertTrue(messages.indexOf("Bob: $60") < messages.indexOf("Alex: $30"), "the highest first");
        execute(alex, "sg bounty Bob");
        assertContains(messages(alex), "The bounty on Bob: $60");
    }

    @Test
    void theBountyIsPaidBackIfNobodyKillsHim() {
        begin();
        execute(steve, "sg bounty Alex 50");
        messages(steve);
        alex.setHealth(0);
        assertContains(messages(steve), "Your bounty of $50 on Alex has been paid back.");
        assertEquals(100, economy.balance(steve));
    }

    @Test
    void theBountyIsPaidBackIfHeWins() {
        begin();
        execute(steve, "sg bounty Alex 50");
        bob.setHealth(0);
        steve.setKiller(alex);
        steve.setHealth(0);
        assertEquals(GameState.ENDED, game.getState());
        assertEquals(100, economy.balance(steve));
        assertContains(messages(steve), "Your bounty of $50 on Alex has been paid back.");
    }

    @Test
    void theBountyIsPaidBackIfTheRoundIsStopped() {
        begin();
        execute(steve, "sg bounty Alex 50");
        plugin.getGameManager().stopGame(game, false);
        assertEquals(100, economy.balance(steve));
    }

    @Test
    void theAmountIsLimited() {
        begin();
        execute(steve, "sg bounty Alex 5");
        assertContains(messages(steve), "A bounty has to be at least $10 (at most: -).");
        config().set("modules.bounty.max", 40);
        execute(steve, "sg bounty Alex 50");
        assertContains(messages(steve), "A bounty has to be at least $10 (at most: $40).");
        execute(bob, "sg bounty Alex 40");
        assertContains(messages(bob), "You don't have $40.");
        assertEquals(0, total(alex));
        assertEquals(100, economy.balance(steve));
        assertEquals(30, economy.balance(bob));
    }

    @Test
    void onlyOnOtherPlayersAlive() {
        begin();
        execute(steve, "sg bounty Steve 20");
        assertContains(messages(steve), "You can't put a bounty on Steve.");
        execute(steve, "sg bounty Nobody 20");
        assertContains(messages(steve), "You can't put a bounty on Nobody.");
        bob.setHealth(0);
        execute(steve, "sg bounty Bob 20");
        assertContains(messages(steve), "You can't put a bounty on Bob.");
        assertEquals(100, economy.balance(steve));
    }

    @Test
    void noBountiesOnTeamMates() {
        game.getArena().setTeamSize(2);
        join();
        execute(steve, "sg team create Red");
        execute(alex, "sg team join Red");
        execute(bob, "sg team create Blue");
        for (int tick = 0; tick < 20 * 10 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        messages(steve);
        execute(steve, "sg bounty Alex 20");
        assertContains(messages(steve), "You can't put a bounty on Alex.");
        execute(steve, "sg bounty Bob 20");
        assertEquals(20, total(bob));
    }

    @Test
    void bountiesNeedARunningGameAndAnEconomy() {
        join();
        execute(steve, "sg bounty Alex 20");
        assertContains(messages(steve), "You can only put bounties on players while the game is running.");

        PlayerMock other = addPlayer("Other");
        execute(other, "sg bounty Alex 20");
        assertContains(messages(other), "You are not in a game.");

        for (int tick = 0; tick < 20 * 10 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        plugin.getEconomySupport().setProvider(null);
        messages(steve);
        execute(steve, "sg bounty Alex 20");
        assertContains(messages(steve), "Bounties need an economy plugin (Vault).");
    }

    @Test
    void bountiesCanBeTurnedOff() {
        config().set("modules.bounty.enabled", false);
        begin();
        execute(steve, "sg bounty Alex 20");
        assertContains(messages(steve), "There are no bounties in this game.");
        assertEquals(100, economy.balance(steve));
    }

    @Test
    void bountiesOfAVaroAreSaved() {
        config().set("varo.start-countdown", 1);
        config().set("varo.combat-radius", 0);
        VaroGame varo = (VaroGame) createGame("varo1", GameType.VARO, 3);
        PlayerMock admin = addAdmin("Admin");
        execute(admin, "sg join varo1");
        execute(steve, "sg join varo1");
        execute(admin, "sg team create Wolves");
        execute(steve, "sg team create Bears");
        execute(admin, "sg game varo1 start");
        seconds(3);
        assertEquals(GameState.RUNNING, varo.getState());

        execute(steve, "sg bounty Admin 40");
        assertEquals(60, economy.balance(steve));
        plugin.getGameManager().stopGame(varo, false);
        assertEquals(60, economy.balance(steve), "the Varo goes on after a restart");

        VaroGame loaded = (VaroGame) plugin.getGameManager().createGame(varo.getArena());
        assertEquals(40, plugin.getBounty().getTotal(loaded, admin.getUniqueId()));

        // a reset ends the game: the money is paid back
        plugin.getGameManager().resetVaro(loaded, true);
        assertEquals(100, economy.balance(steve));
    }

}
