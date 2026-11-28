package com.christian34.survivalgames.game;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.lib.Items;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.modules.Quell;
import com.christian34.survivalgames.user.GamePlayer;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * A round of Survival Games: the players loot the chests (filled when they are opened for the first time, refilled
 * every few minutes) of a copy of the map, they may vote for the map in the lobby and some rounds are a quell. The rest
 * works like every round ({@link RoundGame}).
 *
 * @author Christian34
 */
public class SurvivalGame extends RoundGame {
    /**
     * the containers whose loot has been created (until the next refill)
     */
    private final Set<Block> filledContainers = new HashSet<>();
    /**
     * the refills of the chests so far (loot.yml: upgrades)
     */
    private int refills;
    /**
     * null: the players don't vote for the map
     */
    @Nullable
    private MapVoting mapVoting;
    /**
     * the rule of the quell (modules.quell), null: a normal round
     */
    @Nullable
    private String quell;
    /**
     * the settings with the changes of the quell
     */
    @Nullable
    private ConfigurationSection quellSettings;

    public SurvivalGame(SurvivalGames plugin, Arena arena) {
        super(plugin, arena);
        setQuell(plugin.getQuell().nextRound(arena));
        if (MapVoting.isEnabled(plugin, arena)) {
            this.mapVoting = new MapVoting(plugin, this);
            // nothing to choose from
            if (!mapVoting.hasChoice()) this.mapVoting = null;
        }
    }

    @Override
    public ConfigurationSection settings() {
        return quellSettings != null ? quellSettings : super.settings();
    }

    /**
     * @return the rule of the quell of this round, null if it is a normal round
     */
    @Nullable
    public String getQuell() {
        return quell;
    }

    /**
     * this round is a quell with the rule (in the lobby, null: a normal round)
     */
    public void setQuell(@Nullable String rule) {
        if (getState() != GameState.LOBBY) return;
        this.quell = rule;
        this.quellSettings = rule == null ? null : plugin.getQuell().apply(super.settings(), rule);
        if (rule != null) broadcast(LangText.QUELL_ANNOUNCED, Quell.describe(rule));
    }

    @Override
    public GameType getType() {
        return GameType.SURVIVAL_GAMES;
    }

    @Override
    protected void onLobbyJoin(GamePlayer gamePlayer) {
        super.onLobbyJoin(gamePlayer);
        if (getMapVoting() != null) gamePlayer.getPlayer().sendMessage(prefixed(LangText.MAP_VOTE_HINT));
        if (quell != null) gamePlayer.getPlayer().sendMessage(prefixed(LangText.QUELL_ANNOUNCED, Quell.describe(quell)));
    }

    @Override
    protected void giveLobbyItems(Player player) {
        super.giveLobbyItems(player);
        if (getMapVoting() != null) {
            player.getInventory().setItem(2, Items.create(Material.MAP, i18n(LangText.ITEM_VOTE),
                    I.itemLines(LangText.ITEM_VOTE_LORE), Items.VOTE));
        }
    }

    // ---------------------------------------------------------------- map voting

    /**
     * @return the map voting while it is open, null if there is none (anymore)
     */
    @Nullable
    public MapVoting getMapVoting() {
        return mapVoting == null || mapVoting.isOver() || getState() != GameState.LOBBY ? null : mapVoting;
    }

    /**
     * the players moved into this game from another lobby (they have voted already)
     */
    void closeVoting() {
        this.mapVoting = null;
    }

    /**
     * the player votes for the map (map menu, /sg vote)
     */
    public void voteMap(Player player, Arena map) {
        MapVoting voting = getMapVoting();
        if (voting == null) {
            player.sendMessage(prefixed(LangText.MAP_VOTE_NONE));
        } else if (getParticipant(player.getUniqueId()) == null) {
            player.sendMessage(prefixed(LangText.NOT_PLAYING));
        } else if (!voting.vote(player, map)) {
            player.sendMessage(prefixed(LangText.MAP_VOTE_UNKNOWN, map.getName()));
        } else {
            player.sendMessage(prefixed(LangText.MAP_VOTED, map.getDisplayName()));
        }
    }

    /**
     * the votes are counted: the players move into the arena that has won
     *
     * @return the game the players are in now
     */
    private Game finishVoting() {
        MapVoting voting = getMapVoting();
        if (voting == null) return this;
        Arena map = voting.finish();
        broadcast(LangText.MAP_VOTE_WON, map.getDisplayName(), voting.getVotes(map));
        if (map == arena) return this;
        Game next = plugin.getGameManager().moveLobby(this, map);
        return next != null ? next : this;
    }

    @Override
    public void start() {
        if (getMapVoting() != null) {
            Game next = finishVoting();
            if (next != this) {
                next.start();
                return;
            }
        }
        super.start();
    }

    // ---------------------------------------------------------------- running

    @Override
    protected void onBegin() {
        plugin.getGameManager().setLastPlayed(arena);
        if (quell != null) showTitle(Title.title(i18n(LangText.QUELL_TITLE), Quell.describe(quell)), null);
        super.onBegin();
    }

    @Override
    protected void onTick() {
        if (getState() == GameState.LOBBY) {
            int end = settings().getInt("map-voting.end-seconds", 10);
            if (getMapVoting() != null && getCountdown() > 0 && getCountdown() <= end) finishVoting();
            return;
        }
        super.onTick();
    }

    @Override
    protected void onRunningTick() {
        int refill = settings().getInt("refill-minutes");
        if (refill > 0 && getRunningSeconds() % (refill * 60) == 0 && !isFinalPhase()) {
            filledContainers.clear();
            refills++;
            broadcast(LangText.CHESTS_REFILLED);
        }
    }

    // ---------------------------------------------------------------- loot

    /**
     * creates the loot of a container when it is opened for the first time (after a refill: again)
     */
    public void fillContainer(Inventory inventory) {
        if (getState() != GameState.RUNNING) return;
        Location location = inventory.getLocation();
        if (location == null) return;
        Block block = location.getBlock();
        if (isPlaced(block) || !filledContainers.add(block)) return;
        Location center = arena.getCenter(getWorld());
        Loot loot = plugin.getLoot();
        double radius = loot.getCenterRadius();
        boolean isCenter = radius > 0 && horizontalDistanceSquared(location, center) <= radius * radius;
        loot.fill(inventory, loot.getTableName(block.getType(), isCenter, refills));
    }

    private static double horizontalDistanceSquared(Location a, Location b) {
        double x = a.getX() - b.getX();
        double z = a.getZ() - b.getZ();
        return x * x + z * z;
    }

    /**
     * @return true for containers that get loot
     */
    public static boolean isLootContainer(Material type) {
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL;
    }

}
