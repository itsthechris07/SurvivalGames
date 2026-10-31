package com.christian34.survivalgames;

import com.christian34.survivalgames.game.DuelsGame;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Duels: the queue, challenges (accept, deny, private arena) and the kit of a duel.
 *
 * @author Christian34
 */
class DuelsTest extends PluginTestBase {
    private PlayerMock steve;
    private PlayerMock alex;
    private DuelsGame duel;

    @BeforeEach
    void setUpDuel() {
        config().set("duels.lobby-countdown", 1);
        config().set("duels.start-countdown", 1);
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
        duel = (DuelsGame) createGame("duel1", GameType.DUELS, 2);
    }

    private void waitForStart(Game game) {
        for (int tick = 0; tick < 20 * 10 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());
    }

    @Test
    void twoPlayersInTheQueueFightEachOther() {
        execute(steve, "sg duel");
        execute(alex, "sg duel");
        assertSame(duel, plugin.getUser(steve).getGame());
        assertSame(duel, plugin.getUser(alex).getGame());
        waitForStart(duel);

        alex.setKiller(steve);
        alex.setHealth(0);
        assertEquals(GameState.ENDED, duel.getState());
        assertEquals("Steve", duel.getWinner());
    }

    @Test
    void theQueueNeedsADuelArena() {
        plugin.getGameManager().stopGame(duel, false);
        messages(steve);
        execute(steve, "sg duel");
        assertContains(messages(steve), "There is no free duel arena right now.");
    }

    @Test
    void anAcceptedChallengeReservesTheArena() {
        execute(steve, "sg duel Alex");
        assertTrue(messages(steve).stream().anyMatch(message -> message.contains("You challenged Alex")));
        assertTrue(messages(alex).stream().anyMatch(message -> message.contains("Steve challenges you to a duel")));

        execute(alex, "sg duel accept Steve");
        assertSame(duel, plugin.getUser(steve).getGame());
        assertSame(duel, plugin.getUser(alex).getGame());
        assertTrue(duel.isReserved());
        assertFalse(duel.isJoinable(), "nobody else can join");

        PlayerMock bob = addPlayer("Bob");
        execute(bob, "sg join duel1");
        assertNull(plugin.getUser(bob).getGame());
        waitForStart(duel);
    }

    @Test
    void aDeniedChallengeIsGone() {
        execute(steve, "sg duel Alex");
        messages(steve);
        execute(alex, "sg duel deny Steve");
        assertContains(messages(steve), "Alex denied your duel.");
        messages(alex);
        execute(alex, "sg duel accept Steve");
        assertTrue(messages(alex).stream().anyMatch(message -> message.contains("hasn't challenged you")));
        assertNull(plugin.getUser(alex).getGame());
    }

    @Test
    void nobodyChallengesThemselves() {
        messages(steve);
        execute(steve, "sg duel Steve");
        assertContains(messages(steve), "You can't challenge yourself.");
    }

    @Test
    void aChallengeExpires() {
        config().set("duels.invite-seconds", 10);
        execute(steve, "sg duel Alex");
        plugin.getDuels().setClock(Clock.offset(Clock.systemDefaultZone(), Duration.ofSeconds(11)));
        messages(alex);
        execute(alex, "sg duel accept Steve");
        assertTrue(messages(alex).stream().anyMatch(message -> message.contains("hasn't challenged you")));
        assertNull(plugin.getUser(alex).getGame());
    }

    @Test
    void everybodyGetsTheKitOfTheChallenge() {
        PlayerMock admin = addAdmin("Admin");
        admin.getInventory().setItem(0, ItemStack.of(Material.BOW));
        plugin.getKits().create("archer", admin, false);
        execute(steve, "sg duel Alex archer");
        assertTrue(messages(alex).stream().anyMatch(message -> message.contains("with the kit archer")));
        execute(alex, "sg duel accept Steve");
        assertEquals("archer", duel.getKitName());
        waitForStart(duel);
        assertTrue(steve.getInventory().contains(Material.BOW));
        assertTrue(alex.getInventory().contains(Material.BOW));
    }

    @Test
    void anUnknownKitIsRejected() {
        messages(steve);
        execute(steve, "sg duel Alex nothing");
        assertContains(messages(steve), "There is no kit called nothing.");
    }

}
