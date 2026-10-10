package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.FakeForms;
import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.lib.Items;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kits: admins save their inventory as kit, the players choose one in the lobby and get it when the game begins.
 *
 * @author Christian34
 */
class KitsTest extends PluginTestBase {
    private SurvivalGame game;
    private PlayerMock admin;
    private PlayerMock steve;
    private PlayerMock alex;

    @BeforeEach
    void setUpGame() {
        config().set("survival-games.lobby-countdown", 1);
        config().set("survival-games.start-countdown", 1);
        config().set("survival-games.grace-period", 0);
        admin = addAdmin("Admin");
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
    }

    /**
     * the admin saves a kit with a sword in the first slot (its icon), a helmet and a shield in the second hand
     */
    private void createKit(String name, String... flags) {
        admin.getInventory().clear();
        admin.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD));
        admin.getInventory().setItem(10, new ItemStack(Material.BREAD, 5));
        admin.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        admin.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        admin.getInventory().setHeldItemSlot(0);
        execute(admin, "sg kit create " + name + (flags.length == 0 ? "" : " " + String.join(" ", flags)));
        admin.getInventory().clear();
    }

    private void createGame() {
        game = (SurvivalGame) createGame("arena1", GameType.SURVIVAL_GAMES, 4);
    }

    private void begin() {
        for (int tick = 0; tick < 20 * 10 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());
    }

    private static boolean hasKit(PlayerMock player) {
        var inventory = player.getInventory();
        return inventory.getItem(0) != null && inventory.getItem(0).getType() == Material.IRON_SWORD
                && inventory.contains(Material.BREAD, 5)
                && inventory.getHelmet() != null && inventory.getHelmet().getType() == Material.IRON_HELMET
                && inventory.getItemInOffHand().getType() == Material.SHIELD;
    }

    @Test
    void adminsSaveTheirInventoryAsKit() {
        createKit("Warrior");
        assertContains(messages(admin), "The kit Warrior has been saved from your inventory");
        Kits.Kit kit = plugin.getKits().get("warrior");
        assertNotNull(kit, "the name doesn't depend on the case");
        assertEquals(Material.IRON_SWORD, kit.icon().getType(), "the item in the hand");
        assertFalse(kit.permission());

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "kits.yml"));
        assertNotNull(saved.getString("kits.Warrior.items"));
        Kits loaded = new Kits(plugin);
        assertEquals(1, loaded.getKits().size());
        assertEquals(Material.SHIELD, loaded.get("Warrior").items()[40].getType(), "the second hand");
    }

    @Test
    void playersChooseAKitInTheLobbyAndGetItAtTheStart() {
        createKit("Warrior");
        createGame();
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        assertTrue(Items.is(steve.getInventory().getItem(4), Items.KITS), "the kit item in the lobby");
        messages(steve);

        execute(steve, "sg kit Warrior");
        assertContains(messages(steve), "You get the kit Warrior when the game begins.");
        begin();
        assertTrue(hasKit(steve));
        assertFalse(hasKit(alex), "he didn't choose one");
        assertFalse(alex.getInventory().contains(Material.IRON_SWORD));
    }

    @Test
    void kitMenu() {
        createKit("Warrior");
        createKit("Vip", "--permission");
        createGame();
        FakeForms forms = new FakeForms();
        plugin.setBedrockForms(forms);
        execute(steve, "sg join arena1");
        execute(steve, "sg kit");
        assertEquals(List.of("Warrior", "Vip", "No kit"), forms.buttons());
        assertContains(forms.lore("Warrior"), "Click to choose");
        assertContains(forms.lore("Warrior"), "4 items");
        assertContains(forms.lore("Vip"), "Locked");

        forms.click(steve, "Warrior");
        assertEquals("Warrior", game.getParticipant(steve.getUniqueId()).getKit());
        execute(steve, "sg kit");
        assertContains(forms.lore("Warrior"), "Chosen");

        forms.click(steve, "Vip");
        assertEquals("Warrior", game.getParticipant(steve.getUniqueId()).getKit(), "not allowed");
        forms.click(steve, "No kit");
        assertNull(game.getParticipant(steve.getUniqueId()).getKit());
        assertContains(messages(steve), "You play without a kit.");
    }

    @Test
    void kitsWithAPermission() {
        createKit("Vip", "--permission");
        assertContains(messages(admin), "Players need the permission sg.kit.vip for it.");
        createGame();
        execute(steve, "sg join arena1");
        PlayerMock vip = addPlayer("Vip", "sg.kit.vip");
        execute(vip, "sg join arena1");
        messages(steve);

        execute(steve, "sg kit Vip");
        assertContains(messages(steve), "You aren't allowed to use the kit Vip.");
        execute(vip, "sg kit vip");
        begin();
        assertFalse(hasKit(steve));
        assertTrue(hasKit(vip));
    }

    @Test
    void theDefaultKitIsForPlayersWhoDidntChoose() {
        createKit("Warrior");
        config().set("modules.kits.default", "warrior");
        createGame();
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        begin();
        assertTrue(hasKit(steve));
        assertTrue(hasKit(alex));
    }

    @Test
    void kitsOnlyInTheLobbyOfGamesWithKits() {
        execute(steve, "sg kit");
        assertContains(messages(steve), "You are not in a game.");

        createGame();
        execute(steve, "sg join arena1");
        assertFalse(Items.is(steve.getInventory().getItem(4), Items.KITS), "there are no kits");
        messages(steve);
        execute(steve, "sg kit");
        assertContains(messages(steve), "You can only choose a kit in the lobby of a game with kits.");

        createKit("Warrior");
        execute(steve, "sg kit Nope");
        assertContains(messages(steve), "There is no kit called Nope.");
        execute(alex, "sg join arena1");
        begin();
        messages(steve);
        execute(steve, "sg kit Warrior");
        assertContains(messages(steve), "You can only choose a kit in the lobby of a game with kits.");
        assertFalse(hasKit(steve));
    }

    @Test
    void kitsOnlyInTheGamesOfTheModule() {
        createKit("Warrior");
        config().set("modules.kits.games", List.of("varo"));
        createGame();
        execute(steve, "sg join arena1");
        assertFalse(plugin.getKits().isUsed(game));
        assertFalse(Items.is(steve.getInventory().getItem(4), Items.KITS));
    }

    @Test
    void adminsManageTheKits() {
        execute(admin, "sg kit list");
        assertContains(messages(admin), "There are no kits yet");
        execute(admin, "sg kit create bad!name");
        assertContains(messages(admin), "Kit names consist of up to 16 letters, digits, _ and -.");

        createKit("Warrior");
        createKit("Vip", "--permission");
        messages(admin);
        execute(admin, "sg kit list");
        var messages = messages(admin);
        assertContains(messages, "Warrior - everybody");
        assertContains(messages, "Vip - sg.kit.vip");

        // the kit goes into the empty inventory to be changed
        admin.getInventory().addItem(new ItemStack(Material.DIRT));
        execute(admin, "sg kit edit Warrior");
        assertContains(messages(admin), "Empty your inventory first");
        admin.getInventory().clear();
        execute(admin, "sg kit edit Warrior");
        assertTrue(hasKit(admin));

        execute(admin, "sg kit delete Warrior");
        assertContains(messages(admin), "The kit Warrior has been deleted.");
        assertNull(plugin.getKits().get("Warrior"));
        execute(admin, "sg kit delete Warrior");
        assertContains(messages(admin), "There is no kit called Warrior.");
    }

    @Test
    void onlyAdminsCreateKits() {
        steve.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD));
        execute(steve, "sg kit create Cheat");
        assertContains(messages(steve), "You do not have permission");
        assertTrue(plugin.getKits().getKits().isEmpty());
    }

}
