package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.FakeEconomy;
import com.christian34.survivalgames.FakeForms;
import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.SurvivalGame;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sponsors: spectators send items to the players alive and pay for them.
 *
 * @author Christian34
 */
class SponsorTest extends PluginTestBase {
    private final FakeEconomy economy = new FakeEconomy();
    private final FakeForms forms = new FakeForms();
    private SurvivalGame game;
    private PlayerMock steve;
    private PlayerMock alex;
    private PlayerMock bob;

    @BeforeEach
    void setUpGame() {
        config().set("survival-games.lobby-countdown", 1);
        config().set("survival-games.start-countdown", 1);
        config().set("survival-games.grace-period", 0);
        config().set("survival-games.deathmatch.players", 0);
        config().set("modules.sponsor.items", List.of(
                Map.of("item", "cooked_beef", "amount", 4, "price", 10),
                Map.of("item", "golden_apple", "price", 50),
                Map.of("item", "bread", "price", 0),
                Map.of("item", "not_an_item", "price", 1)));
        plugin.getEconomySupport().setProvider(economy);
        plugin.setBedrockForms(forms);
        game = (SurvivalGame) createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
        bob = addPlayer("Bob");
        economy.set(bob, 30);
    }

    /**
     * the game runs, Bob has died and watches
     */
    private void begin() {
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        execute(bob, "sg join arena1");
        for (int tick = 0; tick < 20 * 10 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());
        bob.setHealth(0);
        bob.respawn();
        server.getScheduler().performOneTick();
        assertTrue(plugin.getUser(bob).isSpectator());
    }

    private Sponsor.Offer offer(Material type) {
        return plugin.getSponsor().getOffers().stream().filter(offer -> offer.item().getType() == type).findFirst()
                .orElseThrow();
    }

    private void send(PlayerMock sponsor, PlayerMock target, Material type) {
        plugin.getSponsor().send(sponsor, game, game.getParticipant(target.getUniqueId()), offer(type));
    }

    @Test
    void offersComeFromTheConfig() {
        List<Sponsor.Offer> offers = plugin.getSponsor().getOffers();
        assertEquals(3, offers.size(), "unknown items are left out");
        assertEquals(4, offers.getFirst().item().getAmount());
        assertEquals(10, offers.getFirst().price());
        assertEquals(1, offer(Material.GOLDEN_APPLE).item().getAmount());
        assertEquals(0, offer(Material.BREAD).price());
    }

    @Test
    void spectatorsSendItems() {
        begin();
        assertContains(messages(bob), "Send items to the players alive: /sg sponsor");
        messages(steve);
        send(bob, steve, Material.COOKED_BEEF);
        assertTrue(steve.getInventory().contains(Material.COOKED_BEEF, 4));
        assertEquals(20, economy.balance(bob));
        assertContains(messages(steve), "Bob has sent you 4x");
        assertContains(messages(bob), "You have sent 4x");
    }

    @Test
    void sponsorMenu() {
        begin();
        execute(bob, "sg sponsor");
        assertEquals(List.of("Alex", "Steve"), forms.buttons().stream().sorted().toList());
        forms.click(bob, "Alex");
        assertEquals(3, forms.last().getButtons().size());
        assertContains(forms.last().getButtons().getFirst().lore().stream().map(FakeForms::plain).toList(), "Price: $10");
        assertContains(forms.last().getButtons().getLast().lore().stream().map(FakeForms::plain).toList(), "Price: free");

        forms.last().getButtons().getFirst().action().accept(bob);
        assertTrue(alex.getInventory().contains(Material.COOKED_BEEF, 4));

        // the items of one player right away
        execute(bob, "sg sponsor Steve");
        assertEquals("Items for Steve", FakeForms.plain(forms.last().getTitle()));
        execute(bob, "sg sponsor Nobody");
        assertContains(messages(bob), "You can't send items to Nobody.");
    }

    @Test
    void sponsorsWaitBetweenTwoItems() {
        begin();
        send(bob, steve, Material.BREAD);
        messages(bob);
        send(bob, steve, Material.BREAD);
        assertContains(messages(bob), "You can send the next item in 30 seconds.");
        assertTrue(steve.getInventory().contains(Material.BREAD, 1));
        assertFalse(steve.getInventory().contains(Material.BREAD, 2));

        config().set("modules.sponsor.cooldown", 0);
        PlayerMock other = addPlayer("Other");
        execute(other, "sg spectate arena1");
        send(other, steve, Material.BREAD);
        send(other, steve, Material.BREAD);
        assertTrue(steve.getInventory().contains(Material.BREAD, 3));
    }

    @Test
    void itemsHaveToBePaid() {
        begin();
        messages(bob);
        send(bob, steve, Material.GOLDEN_APPLE);
        assertContains(messages(bob), "You don't have $50.");
        assertFalse(steve.getInventory().contains(Material.GOLDEN_APPLE));
        assertEquals(30, economy.balance(bob));

        plugin.getEconomySupport().setProvider(null);
        send(bob, steve, Material.COOKED_BEEF);
        assertContains(messages(bob), "Sending items needs an economy plugin (Vault).");
        // free items don't need one
        send(bob, steve, Material.BREAD);
        assertTrue(steve.getInventory().contains(Material.BREAD));
    }

    @Test
    void onlySpectatorsOfARunningGame() {
        execute(steve, "sg join arena1");
        execute(steve, "sg sponsor");
        assertContains(messages(steve), "Only spectators can send items while the game is running.");
        execute(steve, "sg leave");
        begin();
        messages(alex);
        execute(alex, "sg sponsor");
        assertContains(messages(alex), "Only spectators can send items while the game is running.");
        send(alex, steve, Material.BREAD);
        assertFalse(steve.getInventory().contains(Material.BREAD));

        // not to players who are out
        send(bob, bob, Material.BREAD);
        assertContains(messages(bob), "You can't send items to Bob.");

        PlayerMock other = addPlayer("Other");
        execute(other, "sg sponsor");
        assertContains(messages(other), "You can't send items in this game.");
    }

    @Test
    void sponsorsCanBeTurnedOff() {
        config().set("modules.sponsor.enabled", false);
        begin();
        assertNotContains(messages(bob), "/sg sponsor");
        execute(bob, "sg sponsor");
        assertContains(messages(bob), "You can't send items in this game.");
    }

}
