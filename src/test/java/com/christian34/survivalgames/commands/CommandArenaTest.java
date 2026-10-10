package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.VaroGame;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * /sg arena: creating arenas, setting them up in a copy of the map and their games.
 *
 * @author Christian34
 */
class CommandArenaTest extends PluginTestBase {
    private PlayerMock admin;

    @BeforeEach
    void setUpAdmin() {
        admin = addAdmin("Admin");
    }

    private Arena arena(String name) {
        return plugin.getArenaManager().get(name);
    }

    @Test
    void createNeedsAMap() {
        execute(admin, "sg arena create a1");
        assertNull(arena("a1"));
        assertContains(messages(admin), "There is no map a1 in maps/");

        createMap("a1");
        execute(admin, "sg arena create a1");
        assertNotNull(arena("a1"));
        assertEquals(GameType.SURVIVAL_GAMES, arena("a1").getType());
        assertContains(messages(admin), "Arena a1 (Survival Games) has been created.");

        execute(admin, "sg arena create A1");
        assertContains(messages(admin), "There already is an arena called A1");
    }

    @Test
    void createWithTypeAndSharedMap() {
        createMap("shared");
        execute(admin, "sg arena create v1 --type varo --map shared");
        assertEquals(GameType.VARO, arena("v1").getType());
        assertEquals("shared", arena("v1").getMapName());
        assertTrue(arena("v1").isPlayable());

        execute(admin, "sg arena create v2 --type tag");
        assertNull(arena("v2"));
        assertContains(messages(admin), "Unknown game type tag");
    }

    @Test
    void generatedArenasNeedNoMap() {
        execute(admin, "sg arena create gen --generate --seed 42");
        Arena arena = arena("gen");
        assertTrue(arena.isGenerated());
        assertEquals(42L, arena.getSeed());
        assertTrue(arena.isPlayable());
    }

    @Test
    void invalidNames() {
        execute(admin, "sg arena create a-b");
        assertContains(messages(admin), "Arena names have 1 to 32 letters");
    }

    @Test
    void onlyAdmins() {
        PlayerMock steve = addPlayer("Steve");
        execute(steve, "sg arena list");
        assertContains(messages(steve), "You do not have permission");
    }

    @Test
    void editingHappensInACopyOfTheMap() {
        createArena("a1", GameType.SURVIVAL_GAMES, 0);
        admin.getInventory().addItem(new ItemStack(Material.DIAMOND));
        execute(admin, "sg arena a1 lobby");
        assertContains(messages(admin), "Go into the map of the arena first: /sg arena a1 edit");

        execute(admin, "sg arena a1 edit");
        var editWorld = plugin.getArenaManager().getEditWorld(arena("a1"));
        assertNotNull(editWorld);
        assertEquals(editWorld, admin.getWorld());
        assertEquals(GameMode.CREATIVE, admin.getGameMode());
        assertTrue(admin.getInventory().isEmpty());

        admin.teleport(new Location(editWorld, 10.3, 70.5, -5.8, 30, 0));
        execute(admin, "sg arena a1 spawn add");
        admin.teleport(new Location(editWorld, -10, 70, 5));
        execute(admin, "sg arena a1 spawn add");
        execute(admin, "sg arena a1 lobby");
        assertContains(messages(admin), "The lobby has been set to -10 70 5.");
        assertEquals(2, arena("a1").getSpawnCount());
        Location spawn = arena("a1").getSpawns(editWorld).getFirst();
        assertEquals(10.5, spawn.getX(), "the middle of the block");
        assertEquals(70.5, spawn.getY(), "on a slab: the height of the feet");
        assertEquals(45, spawn.getYaw(), "looking straight");

        execute(admin, "sg arena save");
        assertEquals(world, admin.getWorld());
        assertTrue(admin.getInventory().contains(Material.DIAMOND));
        assertNull(plugin.getArenaManager().getEditWorld(arena("a1")));
        assertContains(messages(admin), "The map of arena a1 has been saved.");
    }

    @Test
    void spawnPoints() {
        createArena("a1", GameType.SURVIVAL_GAMES, 3);
        execute(admin, "sg arena a1 spawn list");
        assertContains(messages(admin), "Spawn points (3) of a1");

        execute(admin, "sg arena a1 spawn remove 5");
        assertContains(messages(admin), "There is no spawn point #5");
        execute(admin, "sg arena a1 spawn remove 1");
        assertEquals(2, arena("a1").getSpawnCount());

        execute(admin, "sg arena a1 spawn clear");
        assertEquals(2, arena("a1").getSpawnCount(), "needs --confirm");
        execute(admin, "sg arena a1 spawn clear --confirm");
        assertEquals(0, arena("a1").getSpawnCount());
    }

    @Test
    void settings() {
        createArena("a1", GameType.SURVIVAL_GAMES, 0);
        execute(admin, "sg arena a1 players 4 2");
        assertContains(messages(admin), "max has to be at least min");
        execute(admin, "sg arena a1 players 2 8");
        assertEquals(8, arena("a1").getMaxPlayers());
        execute(admin, "sg arena a1 teamsize 2");
        assertEquals(2, arena("a1").getTeamSize());
        execute(admin, "sg arena a1 border 300");
        assertEquals(300, arena("a1").getBorderSize());
        execute(admin, "sg arena a1 displayname <gold>Castle");
        assertEquals("Castle", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(arena("a1").getDisplayName()));
        execute(admin, "sg arena a1 type varo");
        assertEquals(GameType.VARO, arena("a1").getType());
    }

    @Test
    void spawnPointsLimitThePlayers() {
        createArena("a1", GameType.SURVIVAL_GAMES, 3);
        arena("a1").setPlayers(2, 10);
        assertEquals(3, arena("a1").getMaxPlayers());
        execute(admin, "sg arena a1 info");
        assertContains(messages(admin), "only 3 spawn points for 10 players");
    }

    @Test
    void gamesOfArenas() {
        createArena("a1", GameType.SURVIVAL_GAMES, 2);
        execute(admin, "sg arena a1 creategame");
        assertNotNull(plugin.getGameManager().getGame(arena("a1")));
        assertTrue(arena("a1").hasAutoGame());

        execute(admin, "sg arena a1 type varo");
        assertContains(messages(admin), "has a game or is being edited");
        assertEquals(GameType.SURVIVAL_GAMES, arena("a1").getType());
        execute(admin, "sg arena a1 delete --confirm");
        assertNotNull(arena("a1"));

        execute(admin, "sg arena a1 removegame");
        assertNull(plugin.getGameManager().getGame(arena("a1")));
        assertFalse(arena("a1").hasAutoGame());

        execute(admin, "sg arena a1 delete");
        assertNotNull(arena("a1"), "needs --confirm");
        execute(admin, "sg arena a1 delete --confirm");
        assertNull(arena("a1"));
    }

    @Test
    void deletingAnArenaDeletesItsSavedVaro() {
        var game = createGame("v1", GameType.VARO, 2);
        java.io.File folder = com.christian34.survivalgames.arena.GameWorld.getFolder(game.getWorld());
        execute(admin, "sg arena v1 removegame");
        assertTrue(VaroGame.getFile(arena("v1")).isFile(), "a stopped Varo is saved");
        assertTrue(folder.isDirectory());

        Arena arena = arena("v1");
        execute(admin, "sg arena v1 delete --confirm");
        assertFalse(VaroGame.getFile(arena).exists());
        assertFalse(folder.exists());
    }

    @Test
    void autoGamesAreCreatedOnStart() {
        createArena("a1", GameType.SURVIVAL_GAMES, 2).setAutoGame(true);
        createArena("a2", GameType.SURVIVAL_GAMES, 2);
        plugin.getGameManager().createAutoGames();
        assertNotNull(plugin.getGameManager().getGame(arena("a1")));
        assertNull(plugin.getGameManager().getGame(arena("a2")));
    }

    @Test
    void listShowsTheArenas() {
        createArena("a1", GameType.SURVIVAL_GAMES, 2);
        createArena("v1", GameType.VARO, 2);
        execute(admin, "sg arena list");
        var messages = messages(admin);
        assertContains(messages, "Arenas (2)");
        assertContains(messages, "a1 | Survival Games | no game");
        assertContains(messages, "v1 | Varo | no game");
    }

    @Test
    void unknownArena() {
        execute(admin, "sg arena nope info");
        assertContains(messages(admin), "There is no arena called nope");
    }

}
