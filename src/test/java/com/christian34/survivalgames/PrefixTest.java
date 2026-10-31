package com.christian34.survivalgames;

import com.christian34.survivalgames.game.GameType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every game type can have its own prefix in front of the messages (`<type>.prefix`), otherwise `prefix` is used.
 *
 * @author Christian34
 */
class PrefixTest extends PluginTestBase {

    @Test
    void theMessagesOfAGameHaveThePrefixOfItsType() {
        config().set("uhc.prefix", "[UHC]");
        plugin.reload();
        createGame("uhc1", GameType.UHC, 0);
        createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        PlayerMock steve = addPlayer("Steve");
        PlayerMock alex = addPlayer("Alex");
        execute(steve, "sg join uhc1");
        execute(alex, "sg join arena1");
        List<String> uhc = messages(steve);
        assertTrue(uhc.stream().anyMatch(message -> message.startsWith("[UHC] Steve has joined")), "uhc: " + uhc);
        List<String> sg = messages(alex);
        assertTrue(sg.stream().anyMatch(message -> message.startsWith("[SG] Alex has joined")), "survival games: " + sg);

        // messages of commands and extras go by the game the player is in
        execute(steve, "sg leave");
        messages(steve);
        execute(steve, "sg leave");
        List<String> outside = messages(steve);
        assertFalse(outside.isEmpty());
        assertTrue(outside.stream().allMatch(message -> message.startsWith("[SG]")), "out of the game: " + outside);
    }

    @Test
    void anEmptyPrefixUsesTheOneOfThePlugin() {
        config().set("varo.prefix", "");
        plugin.reload();
        assertEquals(SurvivalGames.PREFIX, SurvivalGames.prefix(GameType.VARO));
        assertEquals(SurvivalGames.PREFIX, SurvivalGames.prefix(null));
    }

}
