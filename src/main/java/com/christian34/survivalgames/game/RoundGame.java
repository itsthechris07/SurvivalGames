package com.christian34.survivalgames.game;

import com.christian34.survivalgames.UsageStats;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.user.GamePlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * A round that lasts a few minutes in a temporary world (Survival Games, UHC, Duels): the players wait in the lobby
 * until enough of them are there, start on the spawn points, can't hurt each other during the grace period and meet
 * in the deathmatch at the end. The last one standing wins, then the arena gets a new round.
 *
 * @author Christian34
 */
public abstract class RoundGame extends Game {
    /**
     * blocks the players placed (they may break them again)
     */
    private final Set<Block> placedBlocks = new HashSet<>();
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
     * the materials of breakable-blocks and placeable-blocks (read once per round)
     */
    @Nullable
    private Set<Material> breakable;
    @Nullable
    private Set<Material> placeable;

    protected RoundGame(SurvivalGames plugin, Arena arena) {
        super(plugin, arena, plugin.getGameManager().takeWorld(arena));
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
        if (participants.size() >= getLobbyLimit()) return i18n(LangText.GAME_FULL);
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

    // ---------------------------------------------------------------- running

    @Override
    protected void onStart() {
        this.startTeams = teams.getTeams().size();
    }

    @Override
    protected void onBegin() {
        // the next round gets a fresh copy of the map, made while this one is running
        plugin.getGameManager().prepareWorld(arena);
        this.graceLeft = Math.max(0, settings().getInt("grace-period"));
        if (graceLeft > 0) {
            broadcast(LangText.SG_STARTED_GRACE, formatDuration(graceLeft));
            showGrace();
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

    @Override
    public boolean isPvpAllowed() {
        return !isGracePeriod();
    }

    /**
     * the time until PvP above the hotbar (the sidebar is easily overlooked)
     */
    private void showGrace() {
        Component bar = i18n(LangText.ACTIONBAR_GRACE, formatTime(graceLeft));
        for (GamePlayer member : getMembers()) {
            member.getPlayer().sendActionBar(bar);
        }
    }

    /**
     * @return the seconds until PvP, 0 if it is on
     */
    public int getGraceLeft() {
        return graceLeft;
    }

    /**
     * @return true in the deathmatch
     */
    @Override
    public boolean isFinalPhase() {
        return deathmatch;
    }

    @Override
    protected void onTick() {
        if (getState() == GameState.ENDED) {
            if (endingIn > 0 && --endingIn == 0) plugin.getGameManager().restart(this);
            return;
        }
        if (getState() != GameState.RUNNING) return;
        if (graceLeft > 0) {
            graceLeft--;
            if (graceLeft == 0) {
                broadcast(LangText.GRACE_OVER);
                showTitle(Title.title(Component.empty(), i18n(LangText.GRACE_OVER_TITLE)), Sound.ENTITY_WITHER_SPAWN);
                giveTrackers();
            } else {
                showGrace();
                // the action bar shows the countdown, the chat only reminds
                if (graceLeft == 60 || graceLeft == 10) broadcast(LangText.GRACE_ENDS, graceLeft);
            }
        }
        onRunningTick();
        tickBorder();
        tickDeathmatch();
        tickReveal();
    }

    /**
     * every second while the round runs (after the grace period has been counted down)
     */
    protected void onRunningTick() {
    }

    /**
     * the world border starts shrinking world-border.shrink-after minutes after the start (warned a minute before)
     */
    private void tickBorder() {
        int size = getStartBorderSize();
        int start = settings().getInt("world-border.shrink-after") * 60;
        int seconds = getRunningSeconds();
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
     * @param section reveal or deathmatch
     * @return true once &lt;section&gt;.minutes have passed or only &lt;section&gt;.players are left
     */
    private boolean isDue(String section) {
        int minutes = settings().getInt(section + ".minutes");
        int players = settings().getInt(section + ".players");
        boolean time = minutes > 0 && getRunningSeconds() >= minutes * 60;
        boolean few = players > 0 && getAlive().size() <= players && participants.size() > players;
        return time || few;
    }

    /**
     * lightnings (without damage) strike where the players alive are every reveal.interval seconds - after
     * reveal.minutes or once only reveal.players are left
     */
    private void tickReveal() {
        int interval = settings().getInt("reveal.interval", 60);
        if (deathmatch || interval <= 0) return;
        if (revealIn < 0) {
            if (!isDue("reveal")) return;
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
            if (!isDue("deathmatch")) return;
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
        plugin.getUsageStats().used(UsageStats.Feature.DEATHMATCH);
        this.deathmatchIn = -1;
        this.graceLeft = 0;
        for (GamePlayer member : getPlayers()) {
            Participant participant = getParticipant(member.getPlayer().getUniqueId());
            if (participant != null && participant.isAlive()) {
                member.getPlayer().teleport(getDeathmatchSpawn(participant));
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
        showTitle(Title.title(i18n(LangText.DEATHMATCH_TITLE), Component.empty()), Sound.ENTITY_ENDER_DRAGON_GROWL);
    }

    /**
     * @return where the participant starts the deathmatch: his spawn point
     */
    protected Location getDeathmatchSpawn(Participant participant) {
        return getSpawn(participant);
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

    // ---------------------------------------------------------------- building

    private boolean allowsBuilding() {
        return settings().getBoolean("building");
    }

    private Set<Material> materials(String key) {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        List<String> names = settings().getStringList(key);
        names.stream().map(name -> Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT)))
                .filter(Objects::nonNull).forEach(materials::add);
        return materials;
    }

    /**
     * @return true if a player may break the block (with building off: only blocks placed in the game and the
     * breakable-blocks)
     */
    public boolean mayBreak(Block block) {
        if (allowsBuilding() || placedBlocks.remove(block)) return true;
        if (breakable == null) this.breakable = materials("breakable-blocks");
        return breakable.contains(block.getType());
    }

    /**
     * @return true if a player may place the block (with building off: only the placeable-blocks)
     */
    public boolean mayPlace(Block block) {
        if (!allowsBuilding()) {
            if (placeable == null) this.placeable = materials("placeable-blocks");
            if (!placeable.contains(block.getType())) return false;
        }
        placedBlocks.add(block);
        return true;
    }

    /**
     * @return true if a player has placed the block in this round
     */
    protected boolean isPlaced(Block block) {
        return placedBlocks.contains(block);
    }

}
