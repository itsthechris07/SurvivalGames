package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.lib.Items;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The modules of a round of Survival Games: the player tracker, supply drops, the shrinking world border, the loot
 * tables of the containers and the lightnings that show where the players are.
 *
 * @author Christian34
 */
class SurvivalGamesModulesTest extends PluginTestBase {
    private static final Pattern COORDINATES = Pattern.compile("at (-?\\d+) (-?\\d+) (-?\\d+)");
    private SurvivalGame game;
    private PlayerMock steve;
    private PlayerMock alex;

    @BeforeEach
    void setUpGame() {
        config().set("survival-games.lobby-countdown", 1);
        config().set("survival-games.start-countdown", 1);
        config().set("survival-games.grace-period", 3);
        config().set("survival-games.deathmatch.minutes", 0);
        config().set("survival-games.deathmatch.players", 0);
        config().set("survival-games.world-border.shrink-after", 0);
        config().set("modules.supply-drop.minutes", 60);
        game = (SurvivalGame) createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
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

    private static boolean hasTracker(PlayerMock player) {
        return Arrays.stream(player.getInventory().getContents()).anyMatch(item -> Items.is(item, Items.TRACKER));
    }

    @Test
    void everybodyGetsATrackerAfterTheGracePeriod() {
        begin();
        assertFalse(hasTracker(steve), "not during the grace period");
        seconds(3);
        assertTrue(hasTracker(steve));
        assertTrue(hasTracker(alex));
        assertContains(messages(steve), "You got a player tracker");

        alex.teleport(new Location(game.getWorld(), 30, 64, -40));
        seconds(1);
        assertEquals(30, steve.getCompassTarget().getBlockX(), "points at the enemy");
        assertEquals(-40, steve.getCompassTarget().getBlockZ());
    }

    @Test
    void trackersOnlyInTheGamesOfTheModule() {
        config().set("modules.tracker.games", List.of("varo"));
        begin();
        seconds(4);
        assertFalse(hasTracker(steve));
    }

    @Test
    void supplyDropsLandWithLoot() {
        config().set("modules.supply-drop.minutes", 1);
        config().set("modules.supply-drop.warning", 3);
        begin();
        seconds(60);
        List<String> messages = messages(steve);
        String announcement = messages.stream().filter(message -> message.contains("A supply drop lands at"))
                .findFirst().orElseThrow(() -> new AssertionError("no announcement in " + messages));
        Matcher matcher = COORDINATES.matcher(announcement);
        assertTrue(matcher.find());
        Block block = game.getWorld().getBlockAt(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)));
        assertTrue(Math.abs(block.getX()) <= 101 && Math.abs(block.getZ()) <= 101, "near the center");
        assertNotEquals(Material.CHEST, block.getType(), "not before the warning is over");

        seconds(3);
        assertContains(messages(steve), "The supply drop has landed");
        assertEquals(Material.CHEST, block.getType());
        assertTrue(plugin.getSupplyDrop().isDrop(block));
        var inventory = ((Chest) block.getState()).getBlockInventory();
        long items = Arrays.stream(inventory.getContents()).filter(Objects::nonNull).count();
        assertTrue(items >= 5 && items <= 8, "between supply-items.min and max: " + items);
    }

    @Test
    void supplyLootComesFromLootYml() {
        var chest = server.createInventory(null, 27);
        chest.addItem(new ItemStack(Material.DIRT));
        plugin.getLoot().fillSupply(chest);
        assertFalse(chest.contains(Material.DIRT), "replaced");
        assertFalse(chest.isEmpty());
    }

    @Test
    void theWorldBorderShrinks() {
        config().set("survival-games.world-border.size", 200);
        config().set("survival-games.world-border.shrink-after", 2);
        config().set("survival-games.world-border.shrink-minutes", 1);
        config().set("survival-games.world-border.final-size", 50);
        begin();
        assertEquals(200, game.getBorderSize());

        seconds(60);
        assertContains(messages(steve), "The world border starts shrinking in 60 seconds!");
        assertEquals(200, game.getBorderSize());
        seconds(60);
        assertContains(messages(steve), "The world border shrinks to 50 blocks within 1 minutes!");
        seconds(30);
        int size = game.getBorderSize();
        assertTrue(size < 200 && size > 50, "shrinking: " + size);
    }


    // loot tables

    private static final String LOOT = """
            items: { min: 2, max: 2 }
            center-radius: 10
            containers:
              barrel: food
              trapped_chest: center
            upgrades:
              1: { food: feast }
              2: { normal: center }
            normal:
              - { item: stick }
            center:
              - { item: diamond }
            food:
              - { item: bread }
            feast:
              - { item: cake }
            """;

    private void useLoot() {
        write("loot.yml", LOOT);
        plugin.reload();
    }

    @Test
    void containersHaveTheirOwnLootTables() {
        useLoot();
        var loot = plugin.getLoot();
        assertEquals("normal", loot.getTableName(Material.CHEST, false, 0));
        assertEquals("center", loot.getTableName(Material.CHEST, true, 0), "near the center");
        assertEquals("center", loot.getTableName(Material.TRAPPED_CHEST, false, 0));
        assertEquals("food", loot.getTableName(Material.BARREL, false, 0));
        assertEquals("food", loot.getTableName(Material.BARREL, true, 0), "the center only replaces the normal loot");

        var barrel = server.createInventory(null, 27);
        loot.fill(barrel, "food");
        assertEquals(2, Arrays.stream(barrel.getContents()).filter(Objects::nonNull).count());
        assertTrue(Arrays.stream(barrel.getContents()).filter(Objects::nonNull).allMatch(item -> item.getType() == Material.BREAD));
        loot.fill(barrel, "unknown");
        assertTrue(barrel.contains(Material.STICK), "the normal loot instead");
    }

    @Test
    void refillsUpgradeTheLoot() {
        useLoot();
        var loot = plugin.getLoot();
        assertEquals("feast", loot.getTableName(Material.BARREL, false, 1));
        assertEquals("normal", loot.getTableName(Material.CHEST, false, 1));
        assertEquals("center", loot.getTableName(Material.CHEST, false, 2));
        assertEquals("feast", loot.getTableName(Material.BARREL, false, 5), "the upgrade stays");
    }

    @Test
    void chestsGetBetterLootAfterRefills() {
        // MockBukkit: the inventory of a chest isn't where its block is - without a radius no chest is near the center
        write("loot.yml", LOOT.replace("center-radius: 10", "center-radius: 0"));
        plugin.reload();
        config().set("survival-games.refill-minutes", 1);
        begin();
        Block block = game.getWorld().getBlockAt(40, 64, 40);
        block.setType(Material.CHEST);
        var inventory = ((Chest) block.getState()).getInventory();
        game.fillContainer(inventory);
        assertTrue(inventory.contains(Material.STICK));

        seconds(120);
        assertContains(messages(steve), "refilled");
        game.fillContainer(inventory);
        assertTrue(inventory.contains(Material.DIAMOND), "the center loot after the second refill");
        assertFalse(inventory.contains(Material.STICK));
    }

    // lightnings

    @Test
    void lightningsShowWhereThePlayersAre() {
        config().set("survival-games.reveal.minutes", 1);
        config().set("survival-games.reveal.interval", 20);
        begin();
        seconds(59);
        assertNotContains(messages(steve), "Lightnings show where every player is!");
        seconds(1);
        assertContains(messages(steve), "Lightnings show where every player is!");
        seconds(19);
        assertNotContains(messages(steve), "Lightnings show");
        seconds(1);
        assertContains(messages(steve), "Lightnings show where every player is!");
    }

    @Test
    void lightningsOnceOnlyAFewPlayersAreLeft() {
        config().set("survival-games.reveal.minutes", 0);
        config().set("survival-games.reveal.players", 2);
        PlayerMock bob = addPlayer("Bob");
        execute(bob, "sg join arena1");
        begin();
        seconds(5);
        assertNotContains(messages(steve), "Lightnings show");
        bob.setHealth(0);
        seconds(1);
        assertContains(messages(steve), "Lightnings show where every player is!");
    }

    @Test
    void noLightningsIfTurnedOff() {
        config().set("survival-games.reveal.minutes", 1);
        config().set("survival-games.reveal.interval", 0);
        begin();
        seconds(70);
        assertNotContains(messages(steve), "Lightnings show");
    }

}
