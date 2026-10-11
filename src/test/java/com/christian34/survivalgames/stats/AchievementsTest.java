package com.christian34.survivalgames.stats;

import com.christian34.survivalgames.FakeForms;
import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.integrations.SgExpansion;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The achievements: reached with the statistics or in a game, kept in stats.yml, shown by /sg achievements and the
 * placeholders.
 *
 * @author Christian34
 */
class AchievementsTest extends PluginTestBase {
    private SurvivalGame game;
    private PlayerMock steve;
    private PlayerMock alex;

    @BeforeEach
    void setUpGame() {
        // the statistics are saved right away
        plugin.getStats().setExecutor(Runnable::run);
        config().set("survival-games.lobby-countdown", 1);
        config().set("survival-games.start-countdown", 1);
        config().set("survival-games.grace-period", 0);
        config().set("survival-games.deathmatch.players", 0);
        game = (SurvivalGame) createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
    }

    private void begin(PlayerMock... players) {
        for (PlayerMock player : players) {
            execute(player, "sg join arena1");
        }
        for (int tick = 0; tick < 20 * 10 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());
    }

    private void kill(PlayerMock victim, PlayerMock killer) {
        victim.setKiller(killer);
        victim.setHealth(0);
    }

    private Set<String> unlocked(PlayerMock player) {
        Map<String, Long> achievements = plugin.getStats().getCachedAchievements(player.getUniqueId());
        assertNotNull(achievements);
        return achievements.keySet();
    }

    @Test
    void aRoundGivesAchievements() {
        begin(steve, alex);
        messages(steve);
        messages(alex);
        kill(alex, steve);
        assertEquals(GameState.ENDED, game.getState());

        assertEquals(Set.of("first_game", "first_kill", "first_win", "first_blood", "naked"), unlocked(steve));
        assertEquals(Set.of("first_game"), unlocked(alex));
        List<String> messages = messages(steve);
        assertContains(messages, "Achievement unlocked! First blood - Get the first kill of a game");
        assertContains(messages, "Achievement unlocked! Victor - Win a game");
        assertNotContains(messages(alex), "Achievement unlocked");

        YamlConfiguration data = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "stats.yml"));
        assertTrue(data.getLong(steve.getUniqueId() + ".achievements.first_blood") > 0, "when he got it");
        assertEquals(Set.of("survival-games"), plugin.getStats().getCached(steve.getUniqueId()).keySet(),
                "the achievements are no game type");
        execute(alex, "sg top wins");
        assertContains(messages(alex), "#1 Steve - 1");
    }

    @Test
    void winningWithoutAKillAndWithOneHeart() {
        begin(steve, alex);
        steve.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        steve.setHealth(2);
        alex.setHealth(0);
        assertEquals(GameState.ENDED, game.getState());
        assertEquals(Set.of("first_game", "first_win", "pacifist", "last_breath"), unlocked(steve));
    }

    @Test
    void killsInOneGame() {
        PlayerMock bob = addPlayer("Bob");
        PlayerMock carl = addPlayer("Carl");
        begin(steve, alex, bob, carl);
        kill(alex, steve);
        kill(bob, steve);
        assertFalse(unlocked(steve).contains("rampage"));
        kill(carl, steve);
        assertTrue(unlocked(steve).contains("rampage"));
        assertFalse(unlocked(steve).contains("unstoppable"), "5 kills");
        assertFalse(unlocked(steve).contains("pacifist"));
        assertFalse(unlocked(bob).contains("first_blood"));
    }

    @Test
    void statisticsOfAllGameTypesCount() {
        plugin.getStats().add(steve.getUniqueId(), "Steve", GameType.SURVIVAL_GAMES, Stat.WINS, 6);
        assertEquals(Set.of("first_win"), unlocked(steve));
        messages(steve);
        plugin.getStats().add(steve.getUniqueId(), "Steve", GameType.VARO, Stat.WINS, 4);
        assertEquals(Set.of("first_win", "champion"), unlocked(steve));
        assertContains(messages(steve), "Achievement unlocked! Champion - Win 10 games");

        plugin.getStats().add(steve.getUniqueId(), "Steve", GameType.VARO, Stat.WINS, 1);
        assertNotContains(messages(steve), "Achievement unlocked");
    }

    @Test
    void achievementsReachedWhileAwayComeWithTheJoin() {
        PlayerMock bob = new PlayerMock(server, "Bob");
        // his team has won a Varo while he was offline
        plugin.getStats().add(bob.getUniqueId(), "Bob", GameType.VARO, Stat.WINS, 1);
        server.addPlayer(bob);
        assertEquals(Set.of("first_win"), unlocked(bob));
        assertContains(messages(bob), "Achievement unlocked! Victor");
    }

    @Test
    void theMenuShowsTheOwnAchievements() {
        FakeForms forms = new FakeForms();
        plugin.setBedrockForms(forms);
        begin(steve, alex);
        kill(alex, steve);
        execute(steve, "sg achievements");
        assertEquals("Achievements (5/" + Achievement.values().length + ")", FakeForms.plain(forms.last().getTitle()));
        assertEquals(Achievement.values().length, forms.buttons().size());
        assertContains(forms.lore("First blood"), "Get the first kill of a game");
        assertContains(forms.lore("First blood"), "Unlocked on ");
        assertContains(forms.lore("Legend"), "Not unlocked yet");
    }

    @Test
    void theAchievementsOfOtherPlayersAreListed() {
        begin(steve, alex);
        kill(alex, steve);
        messages(alex);
        execute(alex, "sg achievements Steve");
        List<String> messages = messages(alex);
        assertContains(messages, "Achievements of Steve (5/" + Achievement.values().length + ")");
        assertContains(messages, "✔ Victor - Win a game (");
        assertContains(messages, "✘ Legend - Win 50 games");

        execute(server.getConsoleSender(), "sg achievements");
        assertContains(messages(server.getConsoleSender()), "Only players");
        execute(server.getConsoleSender(), "sg achievements Nobody");
        assertContains(messages(server.getConsoleSender()), "Achievements of Nobody (0/");
    }

    @Test
    void placeholdersCountTheAchievements() {
        begin(steve, alex);
        kill(alex, steve);
        SgExpansion expansion = new SgExpansion(plugin);
        assertEquals("5", expansion.onRequest(steve, "achievements"));
        assertEquals("1", expansion.onRequest(alex, "achievements"));
        assertEquals(String.valueOf(Achievement.values().length), expansion.onRequest(steve, "achievements_total"));
    }

    @Test
    void achievementsCanBeTurnedOff() {
        config().set("stats.achievements.enabled", false);
        begin(steve, alex);
        messages(steve);
        kill(alex, steve);
        assertTrue(unlocked(steve).isEmpty());
        assertNotContains(messages(steve), "Achievement unlocked");
        execute(steve, "sg achievements");
        assertContains(messages(steve), "The achievements are turned off");
    }

    @Test
    void everyAchievementHasItsTexts() {
        for (Achievement achievement : Achievement.values()) {
            assertNotNull(achievement.getDisplayName());
            assertNotNull(achievement.getDescription());
            assertFalse(achievement.getIcon().isAir());
        }
    }

}
