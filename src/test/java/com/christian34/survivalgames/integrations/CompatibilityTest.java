package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Other plugins changing players or worlds: TAB, EssentialsX, vanish plugins, Multiverse and the state of the game on
 * join NPCs - with fakes of the plugins
 *
 * @author Christian34
 */
class CompatibilityTest extends PluginTestBase {
    private Game game;

    @BeforeEach
    void setUpGame() {
        config().set("survival-games.lobby-countdown", 300);
        game = createGame("arena1", GameType.SURVIVAL_GAMES, 4);
    }

    private static String plain(@Nullable Component component) {
        return component == null ? null : PlainTextComponentSerializer.plainText().serialize(component);
    }

    @SuppressWarnings("deprecation")
    private void vanish(Player player) {
        player.setMetadata(VanishSupport.METADATA, new FixedMetadataValue(plugin, true));
    }

    /**
     * two players join and the game runs
     */
    private void startGame() {
        execute(addPlayer("Steve"), "sg join arena1");
        execute(addPlayer("Alex"), "sg join arena1");
        game.start();
        seconds(15);
        assertEquals(GameState.RUNNING, game.getState());
    }

    // TAB

    private static class FakeTab implements TabSupport {
        final Map<Player, String> takenOver = new HashMap<>();
        final List<Player> released = new ArrayList<>();

        @Override
        public void takeOver(@NotNull Player player, @Nullable Component tabName, boolean sidebar) {
            takenOver.put(player, plain(tabName));
        }

        @Override
        public void release(@NotNull Player player) {
            if (takenOver.remove(player) != null) released.add(player);
        }
    }

    @Test
    void tabDoesNotOverwriteTheTabListInGames() {
        FakeTab tab = new FakeTab();
        plugin.setTabSupport(tab);
        PlayerMock player = addPlayer("Player");
        execute(player, "sg join arena1");
        seconds(1);
        assertTrue(tab.takenOver.containsKey(player));
        assertNull(tab.takenOver.get(player), "no team yet: the name of TAB");

        game.start();
        seconds(1);
        assertTrue(tab.takenOver.get(player).contains("Player"), "the name with the team");

        execute(player, "sg leave");
        assertFalse(tab.takenOver.containsKey(player));
        assertEquals(List.of(player), tab.released);
        seconds(1);
        assertFalse(tab.takenOver.containsKey(player), "not taken over again outside of the game");
    }

    @Test
    void tabIsReleasedForSpectatorsLeaving() {
        FakeTab tab = new FakeTab();
        plugin.setTabSupport(tab);
        startGame();
        PlayerMock spectator = addPlayer("Spectator");
        execute(spectator, "sg spectate arena1");
        seconds(1);
        assertTrue(tab.takenOver.containsKey(spectator));
        execute(spectator, "sg leave");
        assertFalse(tab.takenOver.containsKey(spectator));
    }

    // EssentialsX

    private static class FakeEssentials implements EssentialsSupport {
        final Map<Player, Boolean> joined = new HashMap<>();
        final List<Player> left = new ArrayList<>();
        final Set<Player> vanished = new HashSet<>();

        @Override
        public void joinGame(@NotNull Player player, boolean spectator) {
            joined.put(player, spectator);
            vanished.remove(player);
        }

        @Override
        public void leaveGame(@NotNull Player player) {
            left.add(player);
        }

        @Override
        public boolean isVanished(@NotNull Player player) {
            return vanished.contains(player);
        }
    }

    @Test
    void essentialsStatesAreTurnedOffInGames() {
        FakeEssentials essentials = new FakeEssentials();
        plugin.setEssentials(essentials);
        PlayerMock player = addPlayer("Player");
        execute(player, "sg join arena1");
        assertEquals(Boolean.FALSE, essentials.joined.get(player));
        execute(player, "sg leave");
        assertEquals(List.of(player), essentials.left);

        startGame();
        PlayerMock spectator = addPlayer("Spectator");
        execute(spectator, "sg spectate arena1");
        assertEquals(Boolean.TRUE, essentials.joined.get(spectator), "spectators keep god mode and vanish");
    }

    @Test
    void essentialsTeleportsDontLeadIntoGames() {
        PlayerMock steve = addPlayer("Steve");
        execute(steve, "sg join arena1");
        Location lobby = game.getLobby();
        PlayerMock outside = addPlayer("Outside");
        assertTrue(EssentialsSupport.blocksTeleport(outside, lobby), "/back into the world of a game");
        assertFalse(EssentialsSupport.blocksTeleport(outside, outside.getLocation()));
        assertTrue(EssentialsSupport.blocksTeleport(steve, lobby.clone().add(5, 0, 0)), "players can't teleport within the game");
        assertFalse(EssentialsSupport.blocksTeleport(steve, outside.getLocation()), "leaving the game is handled by the game");
        assertTrue(EssentialsSupport.isPlaying(steve));
        assertFalse(EssentialsSupport.isPlaying(outside));
    }

    // vanish plugins

    @Test
    void vanishedPlayersCantPlay() {
        PlayerMock player = addPlayer("Player");
        vanish(player);
        assertTrue(VanishSupport.isVanished(player));
        execute(player, "sg join arena1");
        assertNull(plugin.getUser(player).getGame());
        assertContains(messages(player), "You are vanished");
    }

    @Test
    void vanishedPlayersCanWatch() {
        startGame();
        PlayerMock admin = addPlayer("Admin");
        vanish(admin);
        execute(admin, "sg join arena1");
        assertSame(game, plugin.getUser(admin).getGame());
        assertTrue(plugin.getUser(admin).isSpectator());
    }

    @Test
    void playersVanishedByEssentialsBecomeVisible() {
        FakeEssentials essentials = new FakeEssentials();
        plugin.setEssentials(essentials);
        PlayerMock player = addPlayer("Player");
        vanish(player);
        essentials.vanished.add(player);
        execute(player, "sg join arena1");
        assertSame(game, plugin.getUser(player).getGame());
    }

    @Test
    void vanishedPlayersDontJoinGameServers() {
        config().set("network.mode", "game");
        config().set("network.arena", "arena1");
        PlayerMock admin = new PlayerMock(server, "Admin");
        vanish(admin);
        server.addPlayer(admin);
        server.getScheduler().performOneTick();
        assertNull(plugin.getUser(admin).getGame());

        PlayerMock player = addPlayer("Player");
        server.getScheduler().performOneTick();
        assertSame(game, plugin.getUser(player).getGame());
    }

    // Multiverse

    @Test
    void multiverseInventoriesIgnoresGames() {
        PlayerMock outside = addPlayer("Outside");
        assertFalse(MultiverseSupport.ignoresInventory(outside, "world", "world_nether"));
        assertTrue(MultiverseSupport.ignoresInventory(outside, "world", "sg_arena1"));
        assertTrue(MultiverseSupport.ignoresInventory(outside, "sgvaro_varo1", "world"));
        PlayerMock steve = addPlayer("Steve");
        execute(steve, "sg join arena1");
        // the inventory of players in a game is saved by the game (e.g. while changing the game mode)
        assertTrue(MultiverseSupport.ignoresInventory(steve, null, null));
        assertFalse(MultiverseSupport.ignoresInventory(outside, null, null));
        assertTrue(MultiverseSupport.isTemporary("sg_arena1"));
        assertFalse(MultiverseSupport.isTemporary("sgvaro_varo1"), "the world of a Varo is kept");
    }

    // state of the game on join NPCs

    private static class FakeNpcs implements NpcStatusDisplay {
        final Map<String, String> names = new HashMap<>();
        final List<String> calls = new ArrayList<>();
        boolean loaded = true;

        @Override
        public boolean exists(@NotNull String id) {
            return loaded;
        }

        @Override
        public @Nullable String showStatus(@NotNull String id, @Nullable String shown, @Nullable Component status) {
            calls.add(id + "=" + plain(status));
            if (status == null) {
                names.remove(id);
                return null;
            }
            names.put(id, plain(status));
            return "shown:" + plain(status);
        }
    }

    private FakeNpcs linkNpc(String id) {
        FakeNpcs npcs = new FakeNpcs();
        plugin.getNpcSupport().addPlugin("Citizens", "citizens", npcs);
        PlayerMock admin = addAdmin("Admin");
        execute(admin, "sg npc link arena1");
        plugin.getNpcSupport().click(admin, "citizens:" + id, "Guide");
        return npcs;
    }

    @Test
    void npcsShowTheStateOfTheGame() {
        FakeNpcs npcs = linkNpc("1");
        plugin.getNpcSupport().updateStatus();
        int max = game.getMaxPlayers();
        assertEquals("Lobby • 0/" + max, npcs.names.get("1"));

        // only changes are sent to the NPC plugin
        plugin.getNpcSupport().updateStatus();
        assertEquals(1, npcs.calls.size());
        execute(addPlayer("Player"), "sg join arena1");
        plugin.getNpcSupport().updateStatus();
        assertEquals("Lobby • 1/" + max, npcs.names.get("1"));

        execute(addPlayer("Player2"), "sg join arena1");
        game.start();
        plugin.getNpcSupport().updateStatus();
        assertEquals("Running • 2/" + max, npcs.names.get("1"));
    }

    @Test
    void npcStateIsRemoved() {
        FakeNpcs npcs = linkNpc("1");
        NpcSupport support = plugin.getNpcSupport();
        support.updateStatus();

        // what the NPC plugin shows is saved: after a restart it knows what to replace
        NpcSupport loaded = new NpcSupport(plugin);
        FakeNpcs reloaded = new FakeNpcs();
        List<String> shownBefore = new ArrayList<>();
        loaded.addPlugin("Citizens", "citizens", (NpcStatusDisplayAdapter) (id, shown, status) -> {
            shownBefore.add(shown);
            return reloaded.showStatus(id, shown, status);
        });
        loaded.updateStatus();
        assertEquals("shown:Lobby • 0/" + game.getMaxPlayers(), shownBefore.getFirst());

        config().set("npcs.show-status", false);
        support.updateStatus();
        assertNull(npcs.names.get("1"));

        config().set("npcs.show-status", true);
        support.updateStatus();
        assertNotNull(npcs.names.get("1"));
        PlayerMock admin = addAdmin("Admin2");
        execute(admin, "sg npc unlink");
        support.click(admin, "citizens:1", "Guide");
        assertNull(npcs.names.get("1"), "unlinked NPCs don't show the state anymore");
    }

    @Test
    void npcsLoadedLaterGetTheState() {
        FakeNpcs npcs = linkNpc("1");
        npcs.loaded = false;
        plugin.getNpcSupport().updateStatus();
        assertTrue(npcs.calls.isEmpty());
        npcs.loaded = true;
        plugin.getNpcSupport().updateStatus();
        assertNotNull(npcs.names.get("1"));
    }

    /**
     * an NPC plugin whose NPCs always exist
     */
    @FunctionalInterface
    private interface NpcStatusDisplayAdapter extends NpcStatusDisplay {
        @Override
        default boolean exists(@NotNull String id) {
            return true;
        }
    }

}
