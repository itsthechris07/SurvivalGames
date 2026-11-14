package com.christian34.survivalgames.stats;

import com.christian34.survivalgames.FakeEconomy;
import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.integrations.Placeholders;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The progression of the players: the progress and rewards of the achievements and the seasons of the statistics.
 *
 * @author Christian34
 */
class ProgressionTest extends PluginTestBase {
    private final List<String> console = new ArrayList<>();
    private SurvivalGame game;
    private PlayerMock steve;
    private PlayerMock alex;

    @BeforeEach
    void setUpGame() {
        plugin.getStats().setExecutor(Runnable::run);
        config().set("survival-games.lobby-countdown", 1);
        config().set("survival-games.start-countdown", 1);
        config().set("survival-games.grace-period", 0);
        config().set("survival-games.deathmatch.players", 0);
        game = (SurvivalGame) createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
        server.getCommandMap().register("test", new Command("rewardlog") {
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
                return console.add(String.join(" ", args));
            }
        });
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
    }

    @Test
    void achievementsShowTheirProgress() {
        playRound();
        ConsoleCommandSenderMock sender = server.getConsoleSender();
        messages(sender);
        execute(sender, "sg achievements Steve");
        List<String> messages = messages(sender);
        assertContains(messages, "✘ Veteran - Play 50 games (1/50)");
        assertContains(messages, "✘ Champion - Win 10 games (1/10)");
        assertTrue(messages.stream().anyMatch(message -> message.startsWith("✔ Victor - Win a game")), messages.toString());
        assertContains(messages, "✘ Pacifist - Win a game without a kill");
    }

    @Test
    void achievementsGiveRewards() {
        FakeEconomy economy = new FakeEconomy();
        plugin.getEconomySupport().setProvider(economy);
        config().set("stats.achievements.money", 20);
        config().set("stats.achievements.commands.all", List.of("rewardlog {player} {achievement}"));
        config().set("stats.achievements.commands.first_win", List.of("rewardlog first win {uuid}"));
        messages(steve);
        playRound();
        assertTrue(console.contains("Steve first_win"), console.toString());
        assertTrue(console.contains("first win " + steve.getUniqueId()), console.toString());
        assertTrue(console.contains("Alex first_game"), console.toString());
        long steveAchievements = console.stream().filter(line -> line.startsWith("Steve ")).count();
        assertEquals(20 * steveAchievements, economy.balance(steve));
        assertContains(messages(steve), "You got $20 for the achievement Victor.");
    }

    @Test
    void aSeasonEnds() {
        config().set("stats.seasons.reset", List.of("rating", "wins"));
        config().set("stats.seasons.rewards.1", List.of("rewardlog {place} {player} {season} {value}"));
        playRound();
        int rating = plugin.getStats().getRating(steve.getUniqueId(), GameType.SURVIVAL_GAMES);
        assertTrue(rating > 1000);
        PlayerMock admin = addAdmin("Admin");
        Seasons seasons = plugin.getSeasons();
        assertEquals("1", Placeholders.value(plugin, steve, "season"));

        execute(admin, "sg season end");
        assertEquals(1, seasons.getNumber(), "not without --confirm");
        assertContains(messages(admin), "This ends season 1: the best players are kept and get their rewards, "
                + "these statistics start at 0 again: rating, wins. [End season]");

        execute(admin, "sg season end --confirm");
        // the results of the storage come back on the main thread
        server.getScheduler().performTicks(2);
        assertEquals(2, seasons.getNumber());
        assertEquals(List.of("1 Steve 1 " + rating), console);
        assertContains(messages(steve), "Season 1 is over - the winner is Steve! Season 2 begins now. [Best players]");
        assertEquals(1000, plugin.getStats().getRating(steve.getUniqueId(), GameType.SURVIVAL_GAMES));
        Map<String, PlayerStats> stats = plugin.getStats().getCached(steve.getUniqueId());
        assertNotNull(stats);
        assertEquals(0, stats.get("survival-games").wins());
        assertEquals(1, stats.get("survival-games").kills(), "kept");

        List<Seasons.Entry> top = seasons.getTop(1, Stat.RATING);
        assertNotNull(top);
        assertEquals("Steve", top.getFirst().name());
        assertEquals(rating, top.getFirst().value());
        assertEquals("Steve", seasons.getTop(1, Stat.WINS).getFirst().name());

        messages(admin);
        execute(admin, "sg season 1");
        List<String> messages = messages(admin);
        assertContains(messages, "#1 Steve - " + rating);
        execute(admin, "sg season");
        assertTrue(messages(admin).contains("Past seasons: [1] "));
        execute(admin, "sg season 7");
        assertContains(messages(admin), "There is no season 7. All seasons: /sg season");
    }

}
