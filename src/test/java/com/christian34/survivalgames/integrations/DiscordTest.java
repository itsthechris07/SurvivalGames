package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.SurvivalGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The messages of the Discord webhook: which events are posted and the JSON.
 *
 * @author Christian34
 */
class DiscordTest extends PluginTestBase {
    private final List<String> posted = new ArrayList<>();
    private SurvivalGame game;
    private PlayerMock steve;
    private PlayerMock alex;

    @BeforeEach
    void setUpDiscord() {
        plugin.getStats().setExecutor(Runnable::run);
        config().set("survival-games.lobby-countdown", 1);
        config().set("survival-games.start-countdown", 1);
        config().set("survival-games.grace-period", 0);
        config().set("survival-games.deathmatch.players", 0);
        config().set("discord.enabled", true);
        config().set("discord.webhook-url", "https://discord.invalid/api/webhooks/1/token");
        config().set("discord.events", List.of("start", "win", "elimination"));
        plugin.getDiscord().setSender(posted::add);
        game = (SurvivalGame) createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
    }

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
    void theRoundIsPosted() {
        playRound();
        assertEquals(3, posted.size(), "start, elimination, win: " + posted);
        assertTrue(posted.get(0).contains("Survival Games has begun in arena1 with 2 players: Alex, Steve"), posted.get(0));
        assertTrue(posted.get(0).contains("\"title\":\"Survival Games - arena1\""), posted.get(0));
        assertTrue(posted.get(1).contains("Alex is out of the game! 1 players left. (killed by Steve)"), posted.get(1));
        assertTrue(posted.get(2).contains("Steve has won in arena1! Kills: Steve (1)"), posted.get(2));
        assertTrue(posted.stream().allMatch(json -> json.contains("\"allowed_mentions\":{\"parse\":[]}")),
                "nobody is pinged");
    }

    @Test
    void onlyTheEventsAndGamesOfTheConfig() {
        config().set("discord.events", List.of("win"));
        playRound();
        assertEquals(1, posted.size());

        posted.clear();
        config().set("discord.games", List.of("varo"));
        assertFalse(plugin.getDiscord().isPosted(Discord.Event.WIN, game));
        config().set("discord.enabled", false);
        assertFalse(plugin.getDiscord().isPosted(Discord.Event.SEASON, null));
    }

    @Test
    void nothingWithoutAWebhook() {
        config().set("discord.webhook-url", "");
        playRound();
        assertTrue(posted.isEmpty());
    }

    @Test
    void theJsonIsEscaped() {
        config().set("discord.username", "SG \"Bot\"");
        String json = plugin.getDiscord().json("a\\b", "line 1\nline 2\t\u0001", 0xFF0000);
        assertEquals("{\"username\":\"SG \\\"Bot\\\"\",\"allowed_mentions\":{\"parse\":[]},\"embeds\":[{\"title\":\"a\\\\b\","
                + "\"description\":\"line 1\\nline 2\\t\\u0001\",\"color\":16711680}]}", json);
    }

}
