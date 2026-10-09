package com.christian34.survivalgames;

import com.christian34.survivalgames.arena.ArenaManager;
import com.christian34.survivalgames.arena.GameWorld;
import com.christian34.survivalgames.commands.CommandManager;
import com.christian34.survivalgames.files.FileManager;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameManager;
import com.christian34.survivalgames.game.Loot;
import com.christian34.survivalgames.game.SignManager;
import com.christian34.survivalgames.integrations.SgExpansion;
import com.christian34.survivalgames.lib.Menu;
import com.christian34.survivalgames.lib.Sidebar;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.listeners.PlayerListener;
import com.christian34.survivalgames.listeners.ProtectionListener;
import com.christian34.survivalgames.listeners.TeleportListener;
import com.christian34.survivalgames.modules.Backpack;
import com.christian34.survivalgames.modules.BuildMode;
import com.christian34.survivalgames.modules.ChestLock;
import com.christian34.survivalgames.modules.GravityGrenade;
import com.christian34.survivalgames.modules.ToxicRain;
import com.christian34.survivalgames.network.NetworkManager;
import com.christian34.survivalgames.user.GamePlayer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Survival Games: the players spawn around the middle of an arena, loot chests and fight until one player (or team)
 * is left. Varo is a game type with the same rules that lasts for days: every player may only play a few minutes per
 * day and the world is kept. A server runs several games next to each other or one game (network: lobby server and
 * game servers).
 *
 * @author Christian34
 */
public class SurvivalGames extends JavaPlugin {
    /**
     * the prefix of all messages, from `prefix` in config.yml (set when the plugin is enabled)
     */
    public static Component PREFIX = Component.empty();
    public static final String ADMIN_PERMISSION = "sg.admin";
    private static SurvivalGames instance;
    private final Map<UUID, GamePlayer> users = new ConcurrentHashMap<>();
    private FileManager fileManager;
    private ArenaManager arenaManager;
    private GameManager gameManager;
    private NetworkManager networkManager;
    private SignManager signManager;
    private Loot loot;
    private CommandManager commandManager;
    private BuildMode buildMode;
    private Backpack backpack;
    private GravityGrenade gravityGrenade;
    private Sidebar sidebar;

    public static SurvivalGames getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        // worlds of games that weren't stopped (crash)
        GameWorld.deleteLeftovers();
        this.fileManager = new FileManager();
        loadTexts();
        this.loot = new Loot(this);
        this.arenaManager = new ArenaManager(this);
        this.gameManager = new GameManager(this);
        this.networkManager = new NetworkManager(this);
        this.buildMode = new BuildMode();
        this.backpack = new Backpack(this);
        this.gravityGrenade = new GravityGrenade(this);
        this.sidebar = new Sidebar(this);
        networkManager.ensureGame();
        gameManager.createAutoGames();
        this.signManager = new SignManager(this);
        try {
            this.commandManager = new CommandManager(this);
        } catch (Exception | LinkageError ex) {
            // cloud reflects into server internals, which may break on server updates (and doesn't work on
            // MockBukkit) - the rest of the plugin keeps working
            Debug.warn("Couldn't register the commands of SurvivalGames: " + ex);
        }
        register(new PlayerListener(this), new ProtectionListener(this), new TeleportListener(this),
                new Menu.ChestListener(), buildMode, backpack, new ChestLock(this), gravityGrenade, new ToxicRain(this));
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new SgExpansion(this).register();
        }
        // a reload: the players who are online already
        for (Player player : Bukkit.getOnlinePlayers()) {
            getUser(player);
        }
    }

    @Override
    public void onDisable() {
        if (gameManager != null) gameManager.stopAll();
        if (arenaManager != null) arenaManager.stopAll();
        if (buildMode != null) buildMode.restoreAll();
        if (backpack != null) backpack.removeRecipe();
        if (gravityGrenade != null) gravityGrenade.removeRecipe();
        users.clear();
    }

    private void register(Listener... listeners) {
        for (Listener listener : listeners) {
            Bukkit.getPluginManager().registerEvents(listener, this);
        }
    }

    /**
     * (re)loads config.yml and the messages
     */
    public void reload() {
        fileManager.reloadConfig();
        loadTexts();
    }

    private void loadTexts() {
        String prefix = fileManager.getConfigFile().getString("prefix");
        PREFIX = prefix == null || prefix.isBlank() ? Component.empty() : I.text(prefix + " ");
        I.load();
    }

    // ---------------------------------------------------------------- players

    public GamePlayer getUser(Player player) {
        GamePlayer gamePlayer = users.get(player.getUniqueId());
        // a new Player object after a rejoin
        if (gamePlayer == null || gamePlayer.getPlayer() != player) {
            gamePlayer = new GamePlayer(player);
            users.put(player.getUniqueId(), gamePlayer);
        }
        return gamePlayer;
    }

    public void removeUser(Player player) {
        users.remove(player.getUniqueId());
    }

    /**
     * players only see the players of their own game (spectators only see each other), players outside of games
     * only see the players outside of games
     */
    public void updateVisibility() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            GamePlayer viewerUser = getUser(viewer);
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (viewer == target) continue;
                GamePlayer targetUser = getUser(target);
                boolean visible = viewerUser.getGame() == targetUser.getGame()
                        && (!targetUser.isSpectator() || viewerUser.isSpectator());
                if (visible && !viewer.canSee(target)) {
                    viewer.showPlayer(this, target);
                } else if (!visible && viewer.canSee(target)) {
                    viewer.hidePlayer(this, target);
                }
            }
        }
    }

    /**
     * @param module the key of the module in config.yml (modules.&lt;module&gt;)
     * @return true if the module is enabled for the type of the game (`modules.<module>.games`)
     */
    public boolean isModuleEnabled(String module, @Nullable Game game) {
        if (game == null || !fileManager.getConfigFile().getData().getBoolean("modules." + module + ".enabled", true)) {
            return false;
        }
        return fileManager.getConfigFile().getData().getStringList("modules." + module + ".games").stream()
                .anyMatch(type -> type.equalsIgnoreCase(game.getType().getKey()));
    }

    // ---------------------------------------------------------------- getters

    public FileManager getFileManager() {
        return fileManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public NetworkManager getNetworkManager() {
        return networkManager;
    }

    public SignManager getSignManager() {
        return signManager;
    }

    public Loot getLoot() {
        return loot;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    /**
     * replaces the command manager (the tests use one without a server behind it)
     */
    public void setCommandManager(CommandManager commandManager) {
        this.commandManager = commandManager;
    }

    public BuildMode getBuildMode() {
        return buildMode;
    }

    public Backpack getBackpack() {
        return backpack;
    }

    public GravityGrenade getGravityGrenade() {
        return gravityGrenade;
    }

    public Sidebar getSidebar() {
        return sidebar;
    }

}
