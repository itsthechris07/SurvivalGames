package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.game.VaroGame;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rules for items in a Varo: banned items and potions, limited enchantments.
 *
 * @author Christian34
 */
class RestrictionsTest extends PluginTestBase {
    private Restrictions restrictions;
    private VaroGame game;
    private PlayerMock admin;
    private PlayerMock steve;

    @BeforeEach
    void setUpGame() {
        config().set("varo.start-countdown", 1);
        config().set("varo.combat-radius", 0);
        config().set("modules.restrictions.banned-items", List.of("totem_of_undying", "minecraft:End_Crystal"));
        config().set("modules.restrictions.banned-potions", List.of("strength"));
        config().set("modules.restrictions.max-enchantments.sharpness", 2);
        config().set("modules.restrictions.max-enchantments.knockback", 0);
        // only to ask it about items - the one of the plugin listens to the events
        restrictions = new Restrictions(plugin);
        game = (VaroGame) createGame("varo1", GameType.VARO, 3);
        admin = addAdmin("Admin");
        steve = addPlayer("Steve");
        execute(admin, "sg join varo1");
        execute(steve, "sg join varo1");
        execute(admin, "sg team create Wolves");
        execute(steve, "sg team create Bears");
        execute(admin, "sg game varo1 start");
        seconds(3);
        assertEquals(GameState.RUNNING, game.getState());
        messages(steve);
    }

    private static ItemStack potion(Material material, PotionType type) {
        ItemStack potion = new ItemStack(material);
        potion.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(type));
        return potion;
    }

    private static ItemStack sword() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        sword.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1);
        sword.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        return sword;
    }

    @Test
    void bannedItems() {
        assertTrue(restrictions.isBanned(new ItemStack(Material.TOTEM_OF_UNDYING)));
        assertTrue(restrictions.isBanned(new ItemStack(Material.END_CRYSTAL)), "with namespace and capitals");
        assertFalse(restrictions.isBanned(new ItemStack(Material.DIAMOND_SWORD)));
        assertFalse(restrictions.isBanned(null));
    }

    @Test
    void bannedPotionsIncludeTheStrongAndLongOnes() {
        assertTrue(restrictions.isBanned(potion(Material.POTION, PotionType.STRENGTH)));
        assertTrue(restrictions.isBanned(potion(Material.SPLASH_POTION, PotionType.STRONG_STRENGTH)));
        assertTrue(restrictions.isBanned(potion(Material.LINGERING_POTION, PotionType.LONG_STRENGTH)));
        assertFalse(restrictions.isBanned(potion(Material.POTION, PotionType.HEALING)));

        config().set("modules.restrictions.banned-potions", List.of("strong_healing"));
        assertTrue(restrictions.isBanned(potion(Material.POTION, PotionType.STRONG_HEALING)));
        assertFalse(restrictions.isBanned(potion(Material.POTION, PotionType.HEALING)), "only the strong one");
    }

    @Test
    void enchantmentsAreLoweredToTheirLimits() {
        ItemStack sword = sword();
        assertTrue(restrictions.limitEnchantments(sword));
        assertEquals(2, sword.getEnchantmentLevel(Enchantment.SHARPNESS));
        assertFalse(sword.containsEnchantment(Enchantment.KNOCKBACK), "0: banned");
        assertEquals(3, sword.getEnchantmentLevel(Enchantment.UNBREAKING), "no limit");
        assertFalse(restrictions.limitEnchantments(sword), "nothing to change anymore");

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        book.editMeta(EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(Enchantment.SHARPNESS, 4, true));
        assertTrue(restrictions.limitEnchantments(book));
        assertEquals(2, ((EnchantmentStorageMeta) book.getItemMeta()).getStoredEnchantLevel(Enchantment.SHARPNESS));
    }

    @Test
    void bannedItemsAreTakenAwayFromThePlayers() {
        steve.getInventory().setItem(3, new ItemStack(Material.TOTEM_OF_UNDYING));
        steve.getInventory().setItem(4, sword());
        steve.getInventory().setItem(5, new ItemStack(Material.BREAD));
        seconds(4);
        assertFalse(steve.getInventory().contains(Material.TOTEM_OF_UNDYING));
        assertEquals(2, steve.getInventory().getItem(4).getEnchantmentLevel(Enchantment.SHARPNESS));
        assertTrue(steve.getInventory().contains(Material.BREAD));
        var messages = messages(steve);
        assertContains(messages, "is banned in this game - it has been taken away.");
        assertContains(messages, "have been lowered to the limits of this game.");
    }

    @Test
    void theRulesOnlyApplyInTheGamesOfTheModule() {
        PlayerMock other = addPlayer("Other");
        other.getInventory().addItem(new ItemStack(Material.TOTEM_OF_UNDYING));
        // Survival Games isn't in modules.restrictions.games
        config().set("survival-games.lobby-countdown", 1);
        config().set("survival-games.start-countdown", 1);
        SurvivalGame sg = (SurvivalGame) createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        PlayerMock alex = addPlayer("Alex");
        PlayerMock bob = addPlayer("Bob");
        execute(alex, "sg join arena1");
        execute(bob, "sg join arena1");
        seconds(4);
        assertEquals(GameState.RUNNING, sg.getState());
        alex.getInventory().addItem(new ItemStack(Material.TOTEM_OF_UNDYING));
        seconds(4);
        assertTrue(other.getInventory().contains(Material.TOTEM_OF_UNDYING), "not in a game");
        assertTrue(alex.getInventory().contains(Material.TOTEM_OF_UNDYING));

        config().set("modules.restrictions.enabled", false);
        steve.getInventory().addItem(new ItemStack(Material.TOTEM_OF_UNDYING));
        seconds(4);
        assertTrue(steve.getInventory().contains(Material.TOTEM_OF_UNDYING), "turned off");
    }

    @Test
    void bannedItemsCantBePickedUp() {
        Item totem = game.getWorld().dropItem(steve.getLocation(), new ItemStack(Material.TOTEM_OF_UNDYING));
        EntityPickupItemEvent event = new EntityPickupItemEvent(steve, totem, 0);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());

        Item bread = game.getWorld().dropItem(steve.getLocation(), new ItemStack(Material.BREAD));
        event = new EntityPickupItemEvent(steve, bread, 0);
        server.getPluginManager().callEvent(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void bannedItemsCantBeUsed() {
        ItemStack crystal = new ItemStack(Material.END_CRYSTAL);
        PlayerInteractEvent event = new PlayerInteractEvent(steve, Action.RIGHT_CLICK_AIR, crystal, null, BlockFace.SELF);
        server.getPluginManager().callEvent(event);
        assertEquals(Event.Result.DENY, event.useItemInHand());
        assertContains(messages(steve), "is banned in this game.");

        event = new PlayerInteractEvent(steve, Action.RIGHT_CLICK_AIR, new ItemStack(Material.BREAD), null, BlockFace.SELF);
        server.getPluginManager().callEvent(event);
        assertNotEquals(Event.Result.DENY, event.useItemInHand());
    }

    @Test
    void bannedPotionsCantBeDrunk() {
        PlayerItemConsumeEvent event = new PlayerItemConsumeEvent(steve, potion(Material.POTION, PotionType.STRONG_STRENGTH),
                EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());

        event = new PlayerItemConsumeEvent(steve, potion(Material.POTION, PotionType.SWIFTNESS), EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        assertFalse(event.isCancelled());
    }

}
