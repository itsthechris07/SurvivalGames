package com.christian34.survivalgames;

import com.christian34.survivalgames.arena.ArenaManager;
import com.christian34.survivalgames.arena.GameWorld;
import com.christian34.survivalgames.commands.CommandManager;
import com.christian34.survivalgames.files.FileManager;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.Duels;
import com.christian34.survivalgames.game.GameManager;
import com.christian34.survivalgames.game.Loot;
import com.christian34.survivalgames.game.SignManager;
import com.christian34.survivalgames.integrations.BedrockForms;
import com.christian34.survivalgames.integrations.EconomySupport;
import com.christian34.survivalgames.integrations.EssentialsSupport;
import com.christian34.survivalgames.integrations.MultiverseSupport;
import com.christian34.survivalgames.integrations.NpcSupport;
import com.christian34.survivalgames.integrations.PartySupport;
import com.christian34.survivalgames.integrations.SgExpansion;
import com.christian34.survivalgames.integrations.SgMiniPlaceholders;
import com.christian34.survivalgames.integrations.TabSupport;
import com.christian34.survivalgames.lib.Menu;
import com.christian34.survivalgames.lib.Sidebar;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.listeners.PlayerListener;
import com.christian34.survivalgames.listeners.ProtectionListener;
import com.christian34.survivalgames.listeners.TeleportListener;
import com.christian34.survivalgames.listeners.UhcListener;
import com.christian34.survivalgames.modules.Backpack;
import com.christian34.survivalgames.modules.Bounty;
import com.christian34.survivalgames.modules.BuildMode;
import com.christian34.survivalgames.modules.ChestLock;
import com.christian34.survivalgames.modules.GravityGrenade;
import com.christian34.survivalgames.modules.InventoryView;
import com.christian34.survivalgames.modules.Kits;
import com.christian34.survivalgames.modules.OfflineNpc;
import com.christian34.survivalgames.modules.Restrictions;
import com.christian34.survivalgames.modules.Sponsor;
import com.christian34.survivalgames.modules.SupplyDrop;
import com.christian34.survivalgames.modules.ToxicRain;
import com.christian34.survivalgames.modules.Tracker;
import com.christian34.survivalgames.network.NetworkManager;
import com.christian34.survivalgames.replay.Replays;
import com.christian34.survivalgames.stats.Leaderboards;
import com.christian34.survivalgames.stats.Achievements;
import com.christian34.survivalgames.stats.StatsManager;
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
    private UhcListener uhcListener;
    private Sidebar sidebar;
    private StatsManager stats;
    private Tracker tracker;
    private SupplyDrop supplyDrop;
    private InventoryView inventoryView;
    private Kits kits;
    private Duels duels;
    private Bounty bounty;
    private Sponsor sponsor;
    private OfflineNpc offlineNpc;
    private Leaderboards leaderboards;
    private EssentialsSupport essentials = EssentialsSupport.NONE;
    private TabSupport tabSupport = TabSupport.NONE;
    private BedrockForms bedrockForms = BedrockForms.NONE;
    private PartySupport partySupport;
    private EconomySupport economySupport;
    private NpcSupport npcSupport;
    private Replays replays;
    private UpdateChecker updateChecker;
    private Achievements achievements;
    private Telemetry telemetry;

    public static SurvivalGames getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        // Multiverse may have loaded worlds of games again
        MultiverseSupport.hook(this);
        // worlds of games that weren't stopped (crash)
        GameWorld.deleteLeftovers();
        this.fileManager = new FileManager();
        loadTexts();
        this.telemetry = new Telemetry(this);
        this.loot = new Loot(this);
        this.stats = new StatsManager(this);
        this.achievements = new Achievements(this);
        this.leaderboards = new Leaderboards(this);
        this.inventoryView = new InventoryView(this);
        this.kits = new Kits(this);
        this.duels = new Duels(this);
        this.bounty = new Bounty(this);
        this.sponsor = new Sponsor(this);
        this.offlineNpc = new OfflineNpc(this);
        this.essentials = EssentialsSupport.create(this);
        this.tabSupport = TabSupport.create(this);
        this.partySupport = new PartySupport(this);
        this.economySupport = new EconomySupport(this);
        this.arenaManager = new ArenaManager(this);
        this.gameManager = new GameManager(this);
        this.networkManager = new NetworkManager(this);
        this.buildMode = new BuildMode();
        this.backpack = new Backpack(this);
        this.gravityGrenade = new GravityGrenade(this);
        this.uhcListener = new UhcListener(this);
        this.sidebar = new Sidebar(this);
        this.tracker = new Tracker(this);
        this.supplyDrop = new SupplyDrop(this);
        // before the games: a Varo that is loaded goes on with its recording
        this.replays = new Replays(this);
        networkManager.ensureGame();
        gameManager.createAutoGames();
        this.signManager = new SignManager(this);
        this.npcSupport = new NpcSupport(this);
        this.bedrockForms = BedrockForms.create(this);
        try {
            this.commandManager = new CommandManager(this);
        } catch (Exception | LinkageError ex) {
            // cloud reflects into server internals, which may break on server updates (and doesn't work on
            // MockBukkit) - the rest of the plugin keeps working
            Debug.warn("Couldn't register the commands of SurvivalGames: " + ex);
        }
        register(new PlayerListener(this), new ProtectionListener(this), new TeleportListener(this),
                new Menu.ChestListener(), buildMode, backpack, new ChestLock(this), gravityGrenade, new ToxicRain(this),
                stats, inventoryView, leaderboards, new Restrictions(this), offlineNpc, replays, uhcListener);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new SgExpansion(this).register();
        }
        if (Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")) {
            try {
                new SgMiniPlaceholders(this).register();
                Debug.info("Hooked into MiniPlaceholders");
            } catch (Exception | LinkageError ex) {
                Debug.warn("Couldn't register the placeholders for MiniPlaceholders: " + ex);
            }
        }
        // a reload: the players who are online already
        for (Player player : Bukkit.getOnlinePlayers()) {
            getUser(player);
        }
        this.updateChecker = new UpdateChecker(this);
        this.updateChecker.start();
        telemetry.start();
    }

    @Override
    public void onDisable() {
        if (gameManager != null) gameManager.stopAll();
        // after the games: their recordings are saved, the viewers get their things back
        if (replays != null) replays.close();
        // the NPCs don't show the state of games that don't exist any more
        if (npcSupport != null) npcSupport.removeStatus();
        if (arenaManager != null) arenaManager.stopAll();
        if (buildMode != null) buildMode.restoreAll();
        if (backpack != null) backpack.removeRecipe();
        if (gravityGrenade != null) gravityGrenade.removeRecipe();
        if (uhcListener != null) uhcListener.removeRecipe();
        if (offlineNpc != null) offlineNpc.removeAll();
        // after the games: their last statistics are saved
        if (stats != null) stats.stop();
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
        stats.reload();
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
        // Paper refuses hidePlayer for a disabled plugin (onDisable: the games are stopped after disabling)
        if (!isEnabled()) return;
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

    public StatsManager getStats() {
        return stats;
    }

    public Tracker getTracker() {
        return tracker;
    }

    public SupplyDrop getSupplyDrop() {
        return supplyDrop;
    }

    public InventoryView getInventoryView() {
        return inventoryView;
    }

    public Duels getDuels() {
        return duels;
    }

    public Kits getKits() {
        return kits;
    }

    public Leaderboards getLeaderboards() {
        return leaderboards;
    }

    public Bounty getBounty() {
        return bounty;
    }

    public Sponsor getSponsor() {
        return sponsor;
    }

    public OfflineNpc getOfflineNpc() {
        return offlineNpc;
    }

    public EssentialsSupport getEssentials() {
        return essentials;
    }

    /**
     * only for the tests: a fake of EssentialsX
     */
    public void setEssentials(EssentialsSupport essentials) {
        this.essentials = essentials;
    }

    public TabSupport getTabSupport() {
        return tabSupport;
    }

    /**
     * only for the tests: a fake of TAB
     */
    public void setTabSupport(TabSupport tabSupport) {
        this.tabSupport = tabSupport;
    }

    public BedrockForms getBedrockForms() {
        return bedrockForms;
    }

    /**
     * only for the tests: a fake of Floodgate
     */
    public void setBedrockForms(BedrockForms bedrockForms) {
        this.bedrockForms = bedrockForms;
    }

    public PartySupport getPartySupport() {
        return partySupport;
    }

    public EconomySupport getEconomySupport() {
        return economySupport;
    }

    public NpcSupport getNpcSupport() {
        return npcSupport;
    }

    public Replays getReplays() {
        return replays;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public Achievements getAchievements() {
        return achievements;
    }

    public Telemetry getTelemetry() {
        return telemetry;
    }

}
