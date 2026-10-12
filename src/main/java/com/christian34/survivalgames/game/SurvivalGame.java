package com.christian34.survivalgames.game;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.Telemetry;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.lib.Items;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.user.GamePlayer;
import net.kyori.adventure.text.Component;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * A round of Survival Games: the players wait in the lobby until enough of them are there, start on the spawn
 * points, loot the chests (filled when they are opened for the first time, refilled every few minutes), can't hurt
 * each other during the grace period and meet in the deathmatch at the end. The last one standing wins, then the
 * arena gets a new round with a fresh copy of the map.
 *
 * @author Christian34
 */
public class SurvivalGame extends Game {
    /**
     * the containers whose loot has been created (until the next refill)
     */
    private final Set<Block> filledContainers = new HashSet<>();
    /**
     * blocks the players placed (they may break them again)
     */
    private final Set<Block> placedBlocks = new HashSet<>();
    /**
     * seconds since the game began
     */
    private int seconds;
    private int graceLeft;
    /**
     * seconds until the players are teleported to the deathmatch, -1: no deathmatch coming
     */
    private int deathmatchIn = -1;
    private boolean deathmatch;
    private int endingIn = -1;
    private int startTeams;
    /**
     * seconds until the next lightnings show where the players are, -1: not yet
     */
    private int revealIn = -1;
    /**
     * the refills of the chests so far (loot.yml: upgrades)
     */
    private int refills;
    /**
     * null: the players don't vote for the map
     */
    @Nullable
    private MapVoting mapVoting;

    public SurvivalGame(SurvivalGames plugin, Arena arena) {
        super(plugin, arena, arena.createWorld(arena.getName() + "_"
                + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0x10000)), false));
        if (MapVoting.isEnabled(plugin, arena)) {
            this.mapVoting = new MapVoting(plugin, this);
            // nothing to choose from
            if (!mapVoting.hasChoice()) this.mapVoting = null;
        }
    }

    @Override
    public GameType getType() {
        return GameType.SURVIVAL_GAMES;
    }

    @Override
    protected void setupWorld(World world) {
        boolean monsters = settings().getBoolean("monsters");
        world.setGameRule(GameRules.SPAWN_MOBS, monsters);
        world.setGameRule(GameRules.SPAWN_MONSTERS, monsters);
        world.setGameRule(GameRules.KEEP_INVENTORY, false);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setStorm(false);
        world.setThundering(false);
        long time = settings().getLong("time", 6000);
        if (time >= 0) {
            world.setGameRule(GameRules.ADVANCE_TIME, false);
            world.setTime(time);
        }
    }

    // ---------------------------------------------------------------- joining and leaving

    @Override
    @Nullable
    public Component getJoinDenial(UUID uuid) {
        if (getState() != GameState.LOBBY) return i18n(LangText.GAME_RUNNING, arena.getName());
        if (participants.size() >= getMaxPlayers()) return i18n(LangText.GAME_FULL);
        return null;
    }

    @Override
    protected void onJoinRunning(GamePlayer gamePlayer) {
        // only possible in the lobby
        makeSpectator(gamePlayer, null);
    }

    @Override
    protected void onLeave(GamePlayer gamePlayer, LeaveReason reason) {
        Player player = gamePlayer.getPlayer();
        Participant participant = getParticipant(player.getUniqueId());
        switch (getState()) {
            case LOBBY -> {
                removeParticipant(player.getUniqueId());
                if (reason != LeaveReason.STOP) {
                    broadcast(LangText.GAME_LEFT, player.getName(), participants.size(), getMaxPlayers());
                }
            }
            case STARTING -> {
                if (participant != null) participant.setAlive(false);
            }
            case RUNNING -> {
                if (participant == null || !participant.isAlive()) return;
                // the items are lost like after a death
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && !item.isEmpty()) player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
                player.getInventory().clear();
                if (reason != LeaveReason.STOP) eliminate(participant, null);
            }
            case ENDED -> {
            }
        }
    }

    /**
     * the lobby countdown starts with enough players, is shortened when the lobby is full and stops if too many
     * players leave
     */
    @Override
    protected void onLobbyChange() {
        if (getState() != GameState.LOBBY) return;
        int players = participants.size();
        int countdown = getCountdown();
        if (countdown < 0 && players >= arena.getMinPlayers()) {
            setCountdown(Math.max(1, settings().getInt("lobby-countdown")));
        } else if (countdown >= 0 && players < arena.getMinPlayers()) {
            setCountdown(-1);
            broadcast(LangText.COUNTDOWN_CANCELLED, players, arena.getMinPlayers());
            getMembers().forEach(member -> member.getPlayer().setLevel(0));
            return;
        }
        int full = Math.max(1, settings().getInt("full-countdown"));
        if (players >= getMaxPlayers() && getCountdown() > full) setCountdown(full);
    }

    @Override
    protected void onLobbyJoin(GamePlayer gamePlayer) {
        super.onLobbyJoin(gamePlayer);
        if (getMapVoting() != null) gamePlayer.getPlayer().sendMessage(I.prefixed(LangText.MAP_VOTE_HINT));
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
            player.sendMessage(I.prefixed(LangText.MAP_VOTE_NONE));
        } else if (getParticipant(player.getUniqueId()) == null) {
            player.sendMessage(I.prefixed(LangText.NOT_PLAYING));
        } else if (!voting.vote(player, map)) {
            player.sendMessage(I.prefixed(LangText.MAP_VOTE_UNKNOWN, map.getName()));
        } else {
            player.sendMessage(I.prefixed(LangText.MAP_VOTED, map.getDisplayName()));
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
    protected void onStart() {
        this.startTeams = teams.getTeams().size();
    }

    @Override
    protected void onBegin() {
        this.seconds = 0;
        plugin.getGameManager().setLastPlayed(arena);
        this.graceLeft = Math.max(0, settings().getInt("grace-period"));
        if (graceLeft > 0) {
            broadcast(LangText.SG_STARTED_GRACE, graceLeft);
        } else {
            broadcast(LangText.SG_STARTED);
            giveTrackers();
        }
        // e.g. one player left during the countdown
        if (startTeams > 1) checkWinner();
    }

    /**
     * @return true during the grace period (players can't hurt each other)
     */
    public boolean isGracePeriod() {
        return getState() != GameState.RUNNING || graceLeft > 0;
    }

    public boolean isDeathmatch() {
        return deathmatch;
    }

    /**
     * @return the seconds since the game began
     */
    public int getSeconds() {
        return seconds;
    }

    @Override
    protected void onTick() {
        if (getState() == GameState.ENDED) {
            if (endingIn > 0 && --endingIn == 0) plugin.getGameManager().restart(this);
            return;
        }
        if (getState() == GameState.LOBBY) {
            int end = settings().getInt("map-voting.end-seconds", 10);
            if (getMapVoting() != null && getCountdown() > 0 && getCountdown() <= end) finishVoting();
            return;
        }
        if (getState() != GameState.RUNNING) return;
        seconds++;
        if (graceLeft > 0) {
            graceLeft--;
            if (graceLeft == 0) {
                broadcast(LangText.GRACE_OVER);
                showTitle(net.kyori.adventure.title.Title.title(Component.empty(), i18n(LangText.GRACE_OVER_TITLE)),
                        Sound.ENTITY_WITHER_SPAWN);
                giveTrackers();
            } else if (COUNTDOWN_MESSAGES.contains(graceLeft)) {
                broadcast(LangText.GRACE_ENDS, graceLeft);
            }
        }
        int refill = settings().getInt("refill-minutes");
        if (refill > 0 && seconds % (refill * 60) == 0 && !deathmatch) {
            filledContainers.clear();
            refills++;
            broadcast(LangText.CHESTS_REFILLED);
        }
        tickBorder();
        tickDeathmatch();
        tickReveal();
    }

    /**
     * the world border starts shrinking world-border.shrink-after minutes after the start (warned a minute before)
     */
    private void tickBorder() {
        int size = arena.getBorderSize();
        int start = settings().getInt("world-border.shrink-after") * 60;
        if (deathmatch || size <= 0 || start <= 0) return;
        if (start > 60 && seconds == start - 60) {
            broadcast(LangText.BORDER_SHRINK_WARNING, 60);
        } else if (seconds == start) {
            WorldBorder border = getWorld().getWorldBorder();
            int finalSize = Math.clamp(settings().getInt("world-border.final-size"), 1, size);
            int minutes = Math.max(1, settings().getInt("world-border.shrink-minutes"));
            if (finalSize >= border.getSize()) return;
            border.changeSize(finalSize, 20L * 60 * minutes);
            broadcast(LangText.BORDER_SHRINKING, finalSize, minutes);
            for (GamePlayer member : getMembers()) {
                member.getPlayer().playSound(member.getPlayer(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.8f);
            }
        }
    }

    /**
     * lightnings (without damage) strike where the players alive are every reveal.interval seconds - after
     * reveal.minutes or once only reveal.players are left
     */
    private void tickReveal() {
        int interval = settings().getInt("reveal.interval", 60);
        if (deathmatch || interval <= 0) return;
        if (revealIn < 0) {
            int minutes = settings().getInt("reveal.minutes");
            int players = settings().getInt("reveal.players");
            boolean time = minutes > 0 && seconds >= minutes * 60;
            boolean few = players > 0 && getAlive().size() <= players && participants.size() > players;
            if (!time && !few) return;
            this.revealIn = 0;
        }
        if (revealIn-- > 0) return;
        this.revealIn = interval - 1;
        broadcast(LangText.REVEAL);
        for (GamePlayer member : getPlayers()) {
            Participant participant = getParticipant(member.getPlayer().getUniqueId());
            if (participant != null && participant.isAlive()) {
                member.getPlayer().getWorld().strikeLightningEffect(member.getPlayer().getLocation());
            }
        }
    }

    private void tickDeathmatch() {
        if (deathmatch) return;
        if (deathmatchIn < 0) {
            int minutes = settings().getInt("deathmatch.minutes");
            int players = settings().getInt("deathmatch.players");
            boolean time = minutes > 0 && seconds >= minutes * 60;
            boolean few = players > 0 && getAlive().size() <= players && participants.size() > players;
            if (!time && !few) return;
            deathmatchIn = Math.max(1, settings().getInt("deathmatch.warning"));
            broadcast(LangText.DEATHMATCH_WARNING, deathmatchIn);
            return;
        }
        deathmatchIn--;
        if (deathmatchIn == 0) {
            startDeathmatch();
        } else if (COUNTDOWN_MESSAGES.contains(deathmatchIn) && deathmatchIn <= 10) {
            broadcast(LangText.DEATHMATCH_COUNTDOWN, deathmatchIn);
        }
    }

    /**
     * the players alive are teleported onto their spawn points, the world border shrinks around the center
     */
    public void startDeathmatch() {
        if (deathmatch || getState() != GameState.RUNNING) return;
        this.deathmatch = true;
        plugin.getTelemetry().used(Telemetry.Feature.DEATHMATCH);
        this.deathmatchIn = -1;
        this.graceLeft = 0;
        for (GamePlayer member : getPlayers()) {
            Participant participant = getParticipant(member.getPlayer().getUniqueId());
            if (participant != null && participant.isAlive()) {
                member.getPlayer().teleport(getSpawn(participant));
                member.getPlayer().setFallDistance(0);
            }
        }
        World world = getWorld();
        WorldBorder border = world.getWorldBorder();
        int size = Math.max(1, settings().getInt("deathmatch.border-size"));
        int finalSize = Math.clamp(settings().getInt("deathmatch.final-border-size"), 1, size);
        border.setCenter(arena.getCenter(world));
        border.setSize(size);
        int minutes = settings().getInt("deathmatch.border-minutes");
        if (minutes > 0 && finalSize < size) border.changeSize(finalSize, 20L * 60 * minutes);
        broadcast(LangText.DEATHMATCH_STARTED, size);
        showTitle(net.kyori.adventure.title.Title.title(i18n(LangText.DEATHMATCH_TITLE), Component.empty()),
                Sound.ENTITY_ENDER_DRAGON_GROWL);
    }

    @Override
    protected void onStop() {
        // a round stopped before its end
        plugin.getBounty().refundAll(this);
    }

    @Override
    protected void onEnd() {
        this.endingIn = Math.max(1, settings().getInt("ending-seconds"));
        for (GamePlayer member : getMembers()) {
            member.getPlayer().setAllowFlight(true);
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
        if (placedBlocks.contains(block) || !filledContainers.add(block)) return;
        Location center = arena.getCenter(getWorld());
        double radius = plugin.getLoot().getCenterRadius();
        boolean isCenter = radius > 0 && horizontalDistanceSquared(location, center) <= radius * radius;
        Loot loot = plugin.getLoot();
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

    // ---------------------------------------------------------------- building

    private boolean allowsBuilding() {
        return settings().getBoolean("building");
    }

    private boolean isListed(String key, Material type) {
        List<String> names = settings().getStringList(key);
        return names.stream().map(name -> Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT)))
                .filter(Objects::nonNull).anyMatch(material -> material == type);
    }

    /**
     * @return true if a player may break the block (with building off: only blocks placed in the game and the
     * breakable-blocks)
     */
    public boolean mayBreak(Block block) {
        if (allowsBuilding() || placedBlocks.remove(block)) return true;
        return isListed("breakable-blocks", block.getType());
    }

    /**
     * @return true if a player may place the block (with building off: only the placeable-blocks)
     */
    public boolean mayPlace(Block block) {
        if (!allowsBuilding() && !isListed("placeable-blocks", block.getType())) return false;
        placedBlocks.add(block);
        return true;
    }

}
