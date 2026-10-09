package com.christian34.survivalgames;

import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.arena.GameWorld;
import com.christian34.survivalgames.commands.CommandManager;
import com.christian34.survivalgames.files.ConfigFile;
import com.christian34.survivalgames.files.FileManager;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.MessageTarget;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts a mocked Paper server with SurvivalGames enabled for every test. Each test gets a fresh plugin data folder
 * and a fresh folder for the worlds of the games.
 *
 * @author Christian34
 */
public abstract class PluginTestBase {
    protected ServerMock server;
    protected SurvivalGames plugin;
    protected TestCommandManager commands;
    protected World world;
    @TempDir
    protected File worlds;

    @BeforeEach
    void setUpServer() {
        this.server = MockBukkit.mock();
        this.world = server.addSimpleWorld("world");
        // MockBukkit doesn't store worlds in folders
        GameWorld.setDimensionsFolder(new File(worlds, "minecraft"));
        this.plugin = MockBukkit.load(SurvivalGames.class);
        this.commands = new TestCommandManager();
        this.plugin.setCommandManager(new CommandManager(commands));
    }

    @AfterEach
    void tearDownServer() {
        MockBukkit.unmock();
        GameWorld.setDimensionsFolder(null);
    }

    protected ConfigFile config() {
        return plugin.getFileManager().getConfigFile();
    }

    protected PlayerMock addPlayer(String name, String... permissions) {
        PlayerMock player = new PlayerMock(server, name);
        for (String permission : permissions) {
            player.addAttachment(plugin, permission, true);
        }
        server.addPlayer(player);
        return player;
    }

    protected PlayerMock addAdmin(String name) {
        return addPlayer(name, SurvivalGames.ADMIN_PERMISSION);
    }

    /**
     * a map in maps/ (MockBukkit doesn't read the world files, so one file is enough)
     */
    protected void createMap(String name) {
        File map = new File(FileManager.getMapsFolder(), name);
        assertTrue(map.mkdirs() || map.isDirectory());
        assertDoesNotThrow(() -> Files.writeString(new File(map, "region.txt").toPath(), name));
    }

    /**
     * an arena with a map, min-players 2, its lobby at 0 100 0 and spawn points in a circle (no game yet)
     */
    protected Arena createArena(String name, GameType type, int spawns) {
        createMap(name);
        Arena arena = plugin.getArenaManager().create(name, type, false, null);
        arena.setPlayers(2, Math.max(2, spawns));
        arena.setLobby(new Location(world, 0.5, 100, 0.5));
        arena.setCenter(new Location(world, 0.5, 64, 0.5));
        for (int i = 0; i < spawns; i++) {
            double angle = 2 * Math.PI * i / spawns;
            arena.addSpawn(new Location(world, Math.round(Math.cos(angle) * 20) + 0.5, 64,
                    Math.round(Math.sin(angle) * 20) + 0.5));
        }
        return arena;
    }

    /**
     * an arena with its game
     */
    protected Game createGame(String name, GameType type, int spawns) {
        Arena arena = createArena(name, type, spawns);
        Game game = plugin.getGameManager().createGame(arena);
        assertNotNull(game.getWorld());
        return game;
    }

    /**
     * runs the command like a player would type it (without the leading slash) and the tasks it schedules
     *
     * @return the exception the command failed with (the error message has been sent to the sender already)
     */
    @Nullable
    protected Throwable execute(@NotNull CommandSender sender, @NotNull String input) {
        Throwable failure = null;
        try {
            commands.commandExecutor().executeCommand(sender, input).join();
        } catch (CompletionException ex) {
            failure = ex.getCause();
        }
        server.getScheduler().performOneTick();
        return failure;
    }

    /**
     * runs the server for the given seconds
     */
    protected void seconds(int seconds) {
        server.getScheduler().performTicks(20L * seconds);
    }

    /**
     * all messages the sender received since the last call, without colors
     */
    protected List<String> messages(@NotNull MessageTarget target) {
        List<String> messages = new ArrayList<>();
        Component message;
        while ((message = target.nextComponentMessage()) != null) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
        return messages;
    }

    protected static void assertContains(List<String> messages, String expected) {
        assertTrue(messages.stream().anyMatch(message -> message.contains(expected)),
                () -> "expected a message containing '" + expected + "' but got " + messages);
    }

    protected static void assertNotContains(List<String> messages, String unexpected) {
        assertTrue(messages.stream().noneMatch(message -> message.contains(unexpected)),
                () -> "expected no message containing '" + unexpected + "' but got " + messages);
    }

    /**
     * fires the pre-login event of the player like the server does (not on the main thread)
     *
     * @return true if the player may join
     */
    // the server creates the event: the constructors that aren't marked for removal need its connection
    @SuppressWarnings("removal")
    protected boolean login(UUID uuid, String name) {
        AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(name, InetAddress.getLoopbackAddress(), uuid);
        Thread thread = Thread.ofPlatform().start(() -> server.getPluginManager().callEvent(event));
        assertDoesNotThrow(() -> thread.join());
        return event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED;
    }

    /**
     * fires the damage event of an attack (PlayerMock#damage applies the damage even if the event has been cancelled)
     *
     * @return false if the attack has been cancelled
     */
    // the server creates the event: the constructors that aren't marked for removal need the DamageModifier maps
    @SuppressWarnings("removal")
    protected boolean attack(PlayerMock attacker, PlayerMock victim) {
        var event = new EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(attacker).withDirectEntity(attacker)
                        .build(), 5);
        server.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    /**
     * @return false if the damage has been cancelled
     */
    @SuppressWarnings("removal")
    protected boolean fall(PlayerMock victim) {
        var event = new EntityDamageEvent(victim, EntityDamageEvent.DamageCause.FALL,
                DamageSource.builder(DamageType.FALL).build(), 5);
        server.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    /**
     * writes text into a file of the data folder
     */
    protected void write(String name, String content) {
        try {
            Files.writeString(new File(plugin.getDataFolder(), name).toPath(), content);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
