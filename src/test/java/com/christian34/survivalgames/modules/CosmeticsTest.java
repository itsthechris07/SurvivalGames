package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.FakeEconomy;
import com.christian34.survivalgames.FakeForms;
import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.stats.Achievement;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Arrow;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The cosmetics: unlocked for free, bought, with an achievement or a permission, chosen in /sg cosmetics and played
 * in the games.
 *
 * @author Christian34
 */
class CosmeticsTest extends PluginTestBase {
    private final FakeForms forms = new FakeForms();
    private final FakeEconomy economy = new FakeEconomy();
    private Cosmetics cosmetics;
    private PlayerMock steve;

    @BeforeEach
    void setUpCosmetics() {
        plugin.getStats().setExecutor(Runnable::run);
        plugin.setBedrockForms(forms);
        plugin.getEconomySupport().setProvider(economy);
        cosmetics = plugin.getCosmetics();
        steve = addPlayer("Steve");
    }

    @Test
    void freeCosmeticsAreChosenInTheMenu() {
        execute(steve, "sg cosmetics");
        assertEquals(List.of("Kill effects", "Arrow trails", "Win effects", "Back"), forms.buttons());
        assertTrue(forms.lore("Kill effects").contains("Selected: None"));
        forms.click(steve, "Kill effects");
        assertTrue(forms.lore("Blood").contains("Click to select"));
        assertTrue(forms.lore("Hearts").contains("$250 - click to buy"));
        assertTrue(forms.lore("Souls").contains("Unlocked with the achievement Hunter"));
        forms.click(steve, "Blood");
        assertContains(messages(steve), "You are using Blood now.");
        assertEquals(Cosmetic.BLOOD, cosmetics.getSelected(steve, Cosmetic.Category.KILL));
        assertTrue(forms.lore("Blood").contains("Selected"));

        // kept with the statistics
        YamlConfiguration stats = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "stats.yml"));
        assertEquals("blood", stats.getString(steve.getUniqueId() + ".settings.cosmetic-kill"));

        forms.click(steve, "None");
        assertNull(cosmetics.getSelected(steve, Cosmetic.Category.KILL));
    }

    @Test
    void cosmeticsWithAPriceAreBought() {
        economy.set(steve, 100);
        cosmetics.select(steve, Cosmetic.HEARTS);
        assertContains(messages(steve), "You can't buy Hearts - it costs $250.");
        assertFalse(cosmetics.owns(steve, Cosmetic.HEARTS));

        economy.set(steve, 300);
        cosmetics.select(steve, Cosmetic.HEARTS);
        assertEquals(50, economy.balance(steve));
        assertContains(messages(steve), "You have bought Hearts for $250.");
        assertEquals(Cosmetic.HEARTS, cosmetics.getSelected(steve, Cosmetic.Category.KILL));

        // only once
        cosmetics.select(steve, Cosmetic.BLOOD);
        cosmetics.select(steve, Cosmetic.HEARTS);
        assertEquals(50, economy.balance(steve));
    }

    @Test
    void achievementsAndPermissionsUnlockCosmetics() {
        cosmetics.select(steve, Cosmetic.SOULS);
        assertContains(messages(steve), "You haven't unlocked Souls yet.");
        plugin.getAchievements().unlock(steve.getUniqueId(), "Steve", Achievement.HUNTER);
        assertTrue(cosmetics.owns(steve, Cosmetic.SOULS));

        assertFalse(cosmetics.owns(steve, Cosmetic.LIGHTNING));
        steve.addAttachment(plugin, "sg.cosmetic.lightning", true);
        assertTrue(cosmetics.owns(steve, Cosmetic.LIGHTNING));

        config().set("modules.cosmetics.unlock.blood", "permission");
        assertFalse(cosmetics.owns(steve, Cosmetic.BLOOD));
        steve.addAttachment(plugin, "sg.cosmetic.*", true);
        assertTrue(cosmetics.owns(steve, Cosmetic.BLOOD));
    }

    @Test
    void theEffectsArePlayedInTheGame() {
        config().set("survival-games.lobby-countdown", 1);
        config().set("survival-games.start-countdown", 1);
        config().set("survival-games.grace-period", 0);
        config().set("survival-games.deathmatch.players", 0);
        SurvivalGame game = (SurvivalGame) createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        PlayerMock alex = addPlayer("Alex");
        cosmetics.select(steve, Cosmetic.BLOOD);
        cosmetics.select(steve, Cosmetic.FLAMES);
        steve.addAttachment(plugin, "sg.cosmetic.flame-ring", true);
        cosmetics.select(steve, Cosmetic.FLAME_RING);
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        for (int tick = 0; tick < 20 * 10 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());

        Arrow arrow = game.getWorld().spawn(steve.getLocation(), Arrow.class);
        arrow.setShooter(steve);
        server.getPluginManager().callEvent(new ProjectileLaunchEvent(arrow));
        server.getScheduler().performTicks(5);

        alex.setKiller(steve);
        assertDoesNotThrow(() -> alex.setHealth(0));
        assertEquals(GameState.ENDED, game.getState());
        assertDoesNotThrow(() -> seconds(6));
    }

    @Test
    void noCosmeticsWithoutStatistics() {
        config().set("stats.enabled", false);
        plugin.reload();
        execute(steve, "sg cosmetics");
        assertContains(messages(steve), "The cosmetics are turned off (modules.cosmetics and stats in config.yml).");
    }

}
