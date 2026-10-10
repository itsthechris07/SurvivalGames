package com.christian34.survivalgames.stats;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.integrations.SgExpansion;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The statistics in stats.yml: a round of Survival Games counts games, wins, kills and deaths, /sg stats and /sg top
 * show them, the placeholders read them.
 *
 * @author Christian34
 */
class StatsTest extends PluginTestBase {
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
        game = (SurvivalGame) createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
    }

    /**
     * Steve kills Alex and wins
     */
    private void playRound() {
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        for (int tick = 0; tick < 20 * 10 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());
        alex.setKiller(steve);
        alex.setHealth(0);
        assertEquals(GameState.ENDED, game.getState());
        messages(steve);
        messages(alex);
    }

    @Test
    void aRoundCountsGamesWinsKillsAndDeaths() {
        playRound();
        YamlConfiguration data = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "stats.yml"));
        String steveStats = steve.getUniqueId() + ".survival-games.";
        assertEquals("Steve", data.getString(steve.getUniqueId() + ".name"));
        assertEquals(1, data.getInt(steveStats + "games"));
        assertEquals(1, data.getInt(steveStats + "wins"));
        assertEquals(1, data.getInt(steveStats + "kills"));
        assertEquals(0, data.getInt(steveStats + "deaths"));
        String alexStats = alex.getUniqueId() + ".survival-games.";
        assertEquals(1, data.getInt(alexStats + "games"));
        assertEquals(0, data.getInt(alexStats + "wins"));
        assertEquals(1, data.getInt(alexStats + "deaths"));
    }

    @Test
    void statsCommandShowsTheStatistics() {
        playRound();
        execute(alex, "sg stats Steve");
        var messages = messages(alex);
        assertContains(messages, "Statistics of Steve");
        assertContains(messages, "Survival Games: 1 games, 1 wins, 1 kills, 0 deaths (K/D 1.00)");

        execute(alex, "sg stats");
        assertContains(messages(alex), "1 games, 0 wins, 0 kills, 1 deaths (K/D 0.00)");
        execute(alex, "sg stats Nobody");
        assertContains(messages(alex), "There are no statistics of Nobody yet.");
    }

    @Test
    void topShowsTheBestPlayers() {
        playRound();
        execute(alex, "sg top kills");
        var messages = messages(alex);
        assertContains(messages, "Top Kills (all games)");
        assertContains(messages, "#1 Steve - 1");
        assertNotContains(messages, "Alex");

        execute(alex, "sg top deaths varo");
        assertContains(messages(alex), "Nobody has any Deaths yet.");
        execute(alex, "sg top points");
        assertContains(messages(alex), "Unknown statistic points");
    }

    @Test
    void placeholdersReadTheCache() {
        playRound();
        SgExpansion expansion = new SgExpansion(plugin);
        assertEquals("1", expansion.onRequest(steve, "stats_wins"));
        assertEquals("1", expansion.onRequest(steve, "stats_survival-games_kills"));
        assertEquals("0", expansion.onRequest(steve, "stats_varo_wins"));
        assertEquals("1.00", expansion.onRequest(steve, "stats_kdr"));
        assertNull(expansion.onRequest(steve, "stats_points"));

        plugin.getStats().refreshTop();
        assertEquals("Steve", expansion.onRequest(alex, "top_wins_1_name"));
        assertEquals("1", expansion.onRequest(alex, "top_wins_1_value"));
        assertEquals("", expansion.onRequest(alex, "top_wins_2_name"));
    }

    @Test
    void statisticsCanBeTurnedOff() {
        config().set("stats.enabled", false);
        plugin.getStats().reload();
        assertFalse(plugin.getStats().isEnabled());
        playRound();
        assertFalse(new File(plugin.getDataFolder(), "stats.yml").exists());
        execute(steve, "sg stats");
        assertContains(messages(steve), "The statistics are turned off");
    }

}
