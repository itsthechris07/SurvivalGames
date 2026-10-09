package com.christian34.survivalgames.game;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.lib.lang.I;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.sign.Side;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [sg] signs: players click them to join the game of an arena (on a lobby server: a game server).
 *
 * @author Christian34
 */
class SignManagerTest extends PluginTestBase {
    private PlayerMock admin;
    private Block block;

    @BeforeEach
    void setUpSign() {
        config().set("survival-games.lobby-countdown", 300);
        createArena("arena1", GameType.SURVIVAL_GAMES, 4);
        admin = addAdmin("Admin");
        block = world.getBlockAt(3, 70, 3);
        block.setType(Material.OAK_SIGN);
    }

    private SignChangeEvent write(PlayerMock player, String... lines) {
        SignChangeEvent event = new SignChangeEvent(block, player,
                new java.util.ArrayList<>(java.util.Arrays.stream(lines).map(line -> (Component) Component.text(line)).toList()), Side.FRONT);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private void click(PlayerMock player) {
        server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block,
                BlockFace.NORTH));
    }

    @Test
    void signJoinsTheGame() {
        SignChangeEvent event = write(admin, "[sg]", "arena1", "", "");
        assertContains(messages(admin), "The sign joins the game in arena1.");
        Game game = plugin.getGameManager().getGame(plugin.getArenaManager().get("arena1"));
        assertNotNull(game, "the arena got a game");
        assertEquals("Survival Games", I.plain(event.line(0)));
        assertEquals("arena1", I.plain(event.line(1)));
        assertEquals("Lobby", I.plain(event.line(2)));
        assertEquals("0/4", I.plain(event.line(3)));

        PlayerMock steve = addPlayer("Steve");
        click(steve);
        assertSame(game, plugin.getUser(steve).getGame());
        assertEquals(1, plugin.getSignManager().getSigns().size());
    }

    @Test
    void onlyAdminsCreateSigns() {
        PlayerMock steve = addPlayer("Steve");
        SignChangeEvent event = write(steve, "[sg]", "arena1", "", "");
        assertEquals("[sg]", I.plain(event.line(0)));
        assertTrue(plugin.getSignManager().getSigns().isEmpty());
    }

    @Test
    void unknownArena() {
        write(admin, "[sg]", "nope", "", "");
        assertContains(messages(admin), "There is no arena called nope");
        assertTrue(plugin.getSignManager().getSigns().isEmpty());
    }

    @Test
    void signsAreRemovedWhileSneaking() {
        write(admin, "[sg]", "arena1", "", "");
        BlockBreakEvent event = new BlockBreakEvent(block, admin);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled(), "not by accident");

        admin.setSneaking(true);
        event = new BlockBreakEvent(block, admin);
        server.getPluginManager().callEvent(event);
        assertFalse(event.isCancelled());
        assertTrue(plugin.getSignManager().getSigns().isEmpty());
    }

    @Test
    void signsAreSaved() {
        write(admin, "[sg]", "arena1", "", "");
        assertEquals(List.copyOf(plugin.getSignManager().getSigns()),
                List.copyOf(new SignManager(plugin).getSigns()));
    }

}
