package com.christian34.survivalgames;

import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.UhcGame;
import com.christian34.survivalgames.listeners.UhcListener;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UHC: a generated world, scattered teams, no natural regeneration, final heal, golden heads and the scenarios.
 *
 * @author Christian34
 */
class UhcGameTest extends PluginTestBase {
    private PlayerMock steve;
    private PlayerMock alex;

    @BeforeEach
    void setUpUhc() {
        config().set("uhc.lobby-countdown", 1);
        config().set("uhc.start-countdown", 1);
        config().set("uhc.grace-period", 0);
        config().set("uhc.pregenerate", false);
        config().set("uhc.world-border.size", 400);
        config().set("uhc.scatter-distance", 30);
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
    }

    private UhcGame startGame() {
        UhcGame game = (UhcGame) createGame("uhc1", GameType.UHC, 0);
        execute(steve, "sg join uhc1");
        execute(alex, "sg join uhc1");
        for (int tick = 0; tick < 20 * 10 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());
        return game;
    }

    @Test
    void arenasOfUhcGetAGeneratedWorld() {
        PlayerMock admin = addAdmin("Admin");
        execute(admin, "sg arena create uhc2 --type uhc");
        Arena arena = plugin.getArenaManager().get("uhc2");
        assertNotNull(arena);
        assertEquals(GameType.UHC, arena.getType());
        assertTrue(arena.isGenerated(), "a new world every round");
        assertTrue(arena.isPlayable());
        assertTrue(arena.getWarnings().stream().noneMatch(warning -> warning.toString().contains("spawn")),
                "the players are scattered, no spawn points needed");
    }

    @Test
    void theTeamsAreScatteredAcrossTheWorld() {
        UhcGame game = startGame();
        World world = game.getWorld();
        assertEquals(Boolean.FALSE, world.getGameRuleValue(GameRules.NATURAL_HEALTH_REGENERATION));
        Location center = game.getArena().getCenter(world);
        Location a = steve.getLocation();
        Location b = alex.getLocation();
        assertEquals(world, a.getWorld());
        assertEquals(world, b.getWorld());
        assertTrue(a.distance(b) >= 30, "the teams start apart: " + a + " / " + b);
        for (Location location : List.of(a, b)) {
            assertTrue(Math.abs(location.getX() - center.getX()) <= 200 && Math.abs(location.getZ() - center.getZ()) <= 200,
                    "inside the world border: " + location);
        }
    }

    @Test
    void everybodyIsHealedOnceByTheFinalHeal() {
        config().set("uhc.final-heal", 1);
        UhcGame game = startGame();
        steve.setHealth(5);
        steve.setFoodLevel(3);
        seconds(59);
        assertEquals(5, steve.getHealth());
        assertTrue(messages(steve).stream().anyMatch(message -> message.contains("Final heal in")));
        seconds(1);
        assertEquals(20, steve.getHealth());
        assertEquals(20, steve.getFoodLevel());
        assertEquals(-1, game.getFinalHealIn());
    }

    @Test
    void deadPlayersDropTheirHead() {
        startGame();
        List<ItemStack> drops = new ArrayList<>();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onDeath(PlayerDeathEvent e) {
                drops.addAll(e.getDrops());
            }
        }, plugin);
        alex.setKiller(steve);
        alex.setHealth(0);
        assertTrue(drops.stream().anyMatch(item -> item != null && item.getType() == Material.PLAYER_HEAD),
                "drops: " + drops);
    }

    @Test
    void aGoldenHeadRegeneratesLongerThanAGoldenApple() {
        startGame();
        ItemStack head = UhcListener.createGoldenHead();
        server.getPluginManager().callEvent(new PlayerItemConsumeEvent(steve, head, EquipmentSlot.HAND));
        server.getScheduler().performOneTick();
        PotionEffect regeneration = steve.getPotionEffect(PotionEffectType.REGENERATION);
        assertNotNull(regeneration);
        assertTrue(regeneration.getDuration() > 20 * 5);
    }

    @Test
    void portalsDoNothing() {
        UhcGame game = startGame();
        Location from = steve.getLocation();
        PlayerPortalEvent event = new PlayerPortalEvent(steve, from, new Location(world, 0, 64, 0));
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        assertEquals(game.getWorld(), steve.getWorld());
    }

    @Test
    void cutcleanSmeltsTheOres() {
        UhcGame game = startGame();
        World world = game.getWorld();
        Block block = world.getBlockAt(5, 60, 5);
        block.setType(Material.IRON_ORE);
        Item drop = world.dropItem(block.getLocation(), ItemStack.of(Material.RAW_IRON, 2));
        server.getPluginManager().callEvent(new BlockDropItemEvent(block, block.getState(), steve,
                new ArrayList<>(List.of(drop))));
        assertEquals(Material.IRON_INGOT, drop.getItemStack().getType());
        assertEquals(2, drop.getItemStack().getAmount());

        config().set("uhc.scenarios", List.of());
        Item other = world.dropItem(block.getLocation(), ItemStack.of(Material.RAW_IRON));
        server.getPluginManager().callEvent(new BlockDropItemEvent(block, block.getState(), steve,
                new ArrayList<>(List.of(other))));
        assertEquals(Material.RAW_IRON, other.getItemStack().getType(), "without the scenario");
    }

    @Test
    void timberFellsTheWholeTree() {
        UhcGame game = startGame();
        World world = game.getWorld();
        for (int y = 70; y < 75; y++) {
            world.getBlockAt(10, y, 10).setType(Material.OAK_LOG);
        }
        // a branch
        world.getBlockAt(11, 74, 11).setType(Material.OAK_LOG);
        // another tree nearby
        world.getBlockAt(14, 70, 10).setType(Material.OAK_LOG);
        Block bottom = world.getBlockAt(10, 70, 10);
        server.getPluginManager().callEvent(new BlockBreakEvent(bottom, steve));
        for (int y = 71; y < 75; y++) {
            assertEquals(Material.AIR, world.getBlockAt(10, y, 10).getType());
        }
        assertEquals(Material.AIR, world.getBlockAt(11, 74, 11).getType());
        assertEquals(Material.OAK_LOG, world.getBlockAt(14, 70, 10).getType(), "not connected");
    }

    @Test
    void theWorldIsGeneratedInTheLobby() {
        config().set("uhc.pregenerate", true);
        config().set("uhc.world-border.size", 64);
        UhcGame game = (UhcGame) createGame("uhc1", GameType.UHC, 0);
        assertTrue(game.getPregenerationProgress() < 100);
        for (int tick = 0; tick < 20 * 30 && game.getPregenerationProgress() < 100; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(100, game.getPregenerationProgress());
    }

}
