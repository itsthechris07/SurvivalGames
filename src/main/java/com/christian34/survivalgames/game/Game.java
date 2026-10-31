package com.christian34.survivalgames.game;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.Telemetry;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.arena.GameWorld;
import com.christian34.survivalgames.arena.Lobby;
import com.christian34.survivalgames.integrations.EconomySupport;
import com.christian34.survivalgames.lib.Items;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.stats.Rating;
import com.christian34.survivalgames.stats.Stat;
import com.christian34.survivalgames.user.GamePlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * A game in an arena: players join it, wait in the lobby, stand on the spawn points during the countdown and play
 * until one team is left. Every arena has at most one game, its world is a copy of the map of the arena.
 * <p>
 * The members are the players in the game right now (playing or watching), the participants the players who take
 * part (in a Varo also while they aren't in the game).
 *
 * @author Christian34
 */
public abstract class Game {
    protected static final Set<Integer> COUNTDOWN_MESSAGES = Set.of(60, 30, 20, 10, 5, 4, 3, 2, 1);
    protected final SurvivalGames plugin;
    protected final Arena arena;
    protected final GameWorld gameWorld;
    /**
     * the copy of the lobby world the players wait in (arenas without a lobby of their own), null: none or the game
     * has begun
     */
    @Nullable
    private GameWorld lobbyWorld;
    protected final Teams teams = new Teams();
    protected final Map<UUID, Participant> participants = new ConcurrentHashMap<>();
    private final List<GamePlayer> members = new CopyOnWriteArrayList<>();
    private final BukkitTask ticker;
    private GameState state = GameState.LOBBY;
    /**
     * seconds left of the lobby or start countdown, -1: none
     */
    private int countdown = -1;
    private boolean stopped;
    /**
     * the participants out of the game so far (the order of {@link Participant#getOut()})
     */
    private int eliminations;
    @Nullable
    private String winner;
    private List<Location> spawns = List.of();
    /**
     * the contents of the backpacks (modules.backpack) by team or player
     */
    private final Map<String, String> backpacks = new ConcurrentHashMap<>();
    /**
     * the bounties (modules.bounty): target -> player who has put it on him -> amount
     */
    private final Map<UUID, Map<UUID, Double>> bounties = new ConcurrentHashMap<>();

    public enum LeaveReason {
        /**
         * /sg leave, the leave item
         */
        COMMAND,
        /**
         * the player left the server
         */
        QUIT,
        /**
         * out of the game (eliminated without spectators, play time over)
         */
        KICKED,
        /**
         * the game stops (admin, end of a round, plugin disabled)
         */
        STOP
    }

    protected Game(SurvivalGames plugin, Arena arena, GameWorld gameWorld) {
        this.plugin = plugin;
        this.arena = arena;
        this.gameWorld = gameWorld;
        World world = gameWorld.load();
        setupWorld(world);
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public Arena getArena() {
        return arena;
    }

    public abstract GameType getType();

    /**
     * the settings of the game type (section of config.yml)
     */
    protected ConfigurationSection settings() {
        return arena.settings();
    }

    public World getWorld() {
        return Objects.requireNonNull(gameWorld.getWorld(), "world of the game isn't loaded");
    }

    public GameWorld getGameWorld() {
        return gameWorld;
    }

    /**
     * @return true if the players of the game may be in the world: its own world or its copy of the lobby world
     */
    public boolean hasWorld(World world) {
        return world.equals(gameWorld.getWorld()) || lobbyWorld != null && world.equals(lobbyWorld.getWorld());
    }

    public GameState getState() {
        return state;
    }

    protected void setState(GameState state) {
        this.state = state;
    }

    public boolean isStopped() {
        return stopped;
    }

    public Teams getTeams() {
        return teams;
    }

    /**
     * the serialized contents of the backpacks of the game (key: team.&lt;team&gt; or player.&lt;uuid&gt;)
     */
    public Map<String, String> getBackpacks() {
        return backpacks;
    }

    /**
     * the bounties of the game (target -> player who has put it on him -> amount, modules.bounty)
     */
    public Map<UUID, Map<UUID, Double>> getBounties() {
        return bounties;
    }

    /**
     * game rules, time and weather of the world (once when the game is created)
     */
    protected abstract void setupWorld(World world);

    // ---------------------------------------------------------------- members and participants

    /**
     * @return the players in the game right now (playing and watching)
     */
    public List<GamePlayer> getMembers() {
        return List.copyOf(members);
    }

    public boolean isMember(Player player) {
        return members.stream().anyMatch(member -> member.getPlayer().equals(player));
    }

    /**
     * @return the players playing in the game right now (not the spectators)
     */
    public List<GamePlayer> getPlayers() {
        return members.stream().filter(member -> !member.isSpectator()).toList();
    }

    public List<GamePlayer> getSpectators() {
        return members.stream().filter(GamePlayer::isSpectator).toList();
    }

    public Collection<Participant> getParticipants() {
        return List.copyOf(participants.values());
    }

    @Nullable
    public Participant getParticipant(UUID uuid) {
        return participants.get(uuid);
    }

    /**
     * @param name the name of a participant (online or not)
     */
    @Nullable
    public Participant findParticipant(String name) {
        return participants.values().stream().filter(p -> p.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public List<Participant> getAlive() {
        return participants.values().stream().filter(Participant::isAlive).toList();
    }

    /**
     * @return the teams with at least one player alive
     */
    public List<GameTeam> getAliveTeams() {
        return teams.getTeams().stream().filter(this::isAlive).toList();
    }

    public boolean isAlive(GameTeam team) {
        return team.getMembers().stream().map(participants::get).anyMatch(p -> p != null && p.isAlive());
    }

    @Nullable
    public GameTeam getTeam(UUID player) {
        return teams.getTeam(player);
    }

    /**
     * @return the name of the participant (also offline), the uuid if unknown
     */
    public String getName(UUID uuid) {
        Participant participant = participants.get(uuid);
        if (participant != null) return participant.getName();
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }

    /**
     * @return the participant, created if the player doesn't take part yet
     */
    protected Participant participate(UUID uuid, String name) {
        Participant participant = participants.computeIfAbsent(uuid, key -> new Participant(key, name));
        participant.setName(name);
        return participant;
    }

    /**
     * the player doesn't take part anymore (and leaves his team)
     */
    protected void removeParticipant(UUID uuid) {
        participants.remove(uuid);
        teams.removeMember(uuid);
    }

    public int getMaxPlayers() {
        return arena.getMaxPlayers();
    }

    // ---------------------------------------------------------------- messages

    public void broadcast(Component message) {
        for (GamePlayer member : members) {
            member.getPlayer().sendMessage(message);
        }
        plugin.getReplays().message(this, message);
    }

    public void broadcast(LangText text, Object... arguments) {
        broadcast(prefixed(text, arguments));
    }

    /**
     * @return the message with the prefix of the game type (`<type>.prefix` in config.yml)
     */
    public Component prefixed(LangText text, Object... arguments) {
        return I.prefixed(getType(), text, arguments);
    }

    public Component prefixed(ComponentLike message) {
        return I.prefixed(getType(), message);
    }

    /**
     * the message goes to the players in the game and the participants online outside of it (Varo)
     */
    public void announce(LangText text, Object... arguments) {
        Component message = prefixed(text, arguments);
        broadcast(message);
        for (Participant participant : participants.values()) {
            Player player = participant.getPlayer();
            if (player != null && !isMember(player)) player.sendMessage(message);
        }
    }

    protected void showTitle(Title title, @Nullable Sound sound) {
        for (GamePlayer member : members) {
            member.getPlayer().showTitle(title);
            if (sound != null) member.getPlayer().playSound(member.getPlayer(), sound, 1f, 1f);
        }
    }

    /**
     * e.g. 14:05
     */
    public static String formatTime(int seconds) {
        seconds = Math.max(0, seconds);
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * e.g. 10 minutes, 1:30 minutes, 45 seconds
     */
    public static Component formatDuration(int seconds) {
        seconds = Math.max(0, seconds);
        if (seconds < 60) return i18n(LangText.DURATION_SECONDS, seconds);
        if (seconds == 60) return i18n(LangText.DURATION_MINUTE);
        if (seconds % 60 == 0) return i18n(LangText.DURATION_MINUTES, seconds / 60);
        return i18n(LangText.DURATION_MINUTES_SECONDS, seconds / 60, String.format("%02d", seconds % 60));
    }

    // ---------------------------------------------------------------- joining and leaving

    /**
     * @return why the player can't join the game right now, null if he can
     */
    @Nullable
    public abstract Component getJoinDenial(UUID uuid);

    /**
     * @return true if players can join the game right now (as a new player in the lobby)
     */
    public boolean isJoinable() {
        return !stopped && state == GameState.LOBBY && participants.size() < getMaxPlayers();
    }

    /**
     * @return true if players can watch the game
     */
    public boolean isSpectatable() {
        return !stopped && (state == GameState.STARTING || state == GameState.RUNNING)
                && settings().getBoolean("spectators");
    }

    /**
     * @return true if the player may watch the game now
     */
    public boolean maySpectate(UUID uuid) {
        return isSpectatable();
    }

    /**
     * the player joins the game (his state outside of the game is saved)
     *
     * @return false if he can't (he got a message why)
     */
    public boolean join(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        if (gamePlayer.getGame() != null) return false;
        Component denial = stopped ? i18n(LangText.GAME_NOT_JOINABLE) : getJoinDenial(player.getUniqueId());
        if (denial != null) {
            player.sendMessage(prefixed(denial));
            return false;
        }
        enter(gamePlayer, false);
        if (state == GameState.LOBBY) {
            participate(player.getUniqueId(), player.getName());
            sendToLobby(player);
            broadcast(LangText.GAME_JOINED, player.getName(), participants.size(), getMaxPlayers());
            onLobbyJoin(gamePlayer);
        } else {
            onJoinRunning(gamePlayer);
        }
        plugin.updateVisibility();
        return true;
    }

    /**
     * the player watches the game
     *
     * @return false if he can't (he got a message why)
     */
    public boolean spectate(GamePlayer gamePlayer) {
        if (gamePlayer.getGame() != null) return false;
        if (!maySpectate(gamePlayer.getPlayer().getUniqueId())) {
            gamePlayer.getPlayer().sendMessage(prefixed(LangText.GAME_NOT_SPECTATABLE));
            return false;
        }
        enter(gamePlayer, true);
        makeSpectator(gamePlayer, null);
        plugin.updateVisibility();
        plugin.getTelemetry().used(Telemetry.Feature.SPECTATE);
        return true;
    }

    private void enter(GamePlayer gamePlayer, boolean spectator) {
        Player player = gamePlayer.getPlayer();
        if (plugin.getBuildMode().isBuilding(player)) plugin.getBuildMode().toggle(player);
        plugin.getReplays().leave(player);
        gamePlayer.getStorage().store();
        gamePlayer.getStorage().cleanPlayer();
        gamePlayer.setGame(this);
        gamePlayer.setSpectator(spectator);
        members.add(gamePlayer);
        plugin.getEssentials().joinGame(player, spectator);
    }

    /**
     * the player only watches from now on (joined as spectator or out of the game)
     *
     * @param location where he starts watching, null: the center of the arena
     */
    protected void makeSpectator(GamePlayer gamePlayer, @Nullable Location location) {
        Player player = gamePlayer.getPlayer();
        gamePlayer.setSpectator(true);
        gamePlayer.getStorage().cleanPlayer();
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(location != null ? location : safe(arena.getCenter(getWorld())));
        player.sendMessage(prefixed(LangText.SPECTATING));
        if (plugin.getSponsor().isUsed(this)) player.sendMessage(prefixed(LangText.SPONSOR_HINT));
        plugin.updateVisibility();
    }

    /**
     * the player leaves the game and gets his state from before back (on a game server of a network: and is sent
     * to the lobby server)
     */
    public void leave(GamePlayer gamePlayer, LeaveReason reason) {
        if (!members.contains(gamePlayer)) return;
        Player player = gamePlayer.getPlayer();
        if (!gamePlayer.isSpectator()) onLeave(gamePlayer, reason);
        members.remove(gamePlayer);
        gamePlayer.setGame(null);
        gamePlayer.setSpectator(false);
        player.closeInventory();
        gamePlayer.getStorage().restore();
        plugin.getSidebar().remove(player);
        plugin.getTabSupport().release(player);
        plugin.getEssentials().leaveGame(player);
        if (hasWorld(player.getWorld())) {
            // no saved location (e.g. a crash) - out of the game world
            player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
        }
        plugin.updateVisibility();
        if (reason == LeaveReason.COMMAND) player.sendMessage(prefixed(LangText.GAME_LEFT_SELF, arena.getDisplayName()));
        if (reason == LeaveReason.COMMAND || reason == LeaveReason.STOP) plugin.getNetworkManager().sendToLobby(player);
        if (!stopped && state == GameState.LOBBY) onLobbyChange();
    }

    /**
     * the player has to leave the game (out of the game, play time over): he gets the message, on a game server of a
     * network he is sent to the lobby server (kicked without one)
     */
    public void kick(GamePlayer gamePlayer, Component message) {
        leave(gamePlayer, LeaveReason.KICKED);
        plugin.getNetworkManager().sendAway(gamePlayer.getPlayer(), message);
    }

    /**
     * a player who plays (not a spectator) leaves the game
     */
    protected abstract void onLeave(GamePlayer gamePlayer, LeaveReason reason);

    /**
     * a player has joined the lobby
     */
    protected void onLobbyJoin(GamePlayer gamePlayer) {
        onLobbyChange();
    }

    /**
     * the number of players in the lobby has changed
     */
    protected void onLobbyChange() {
    }

    /**
     * a player joins while the game is starting or running
     */
    protected abstract void onJoinRunning(GamePlayer gamePlayer);

    // ---------------------------------------------------------------- lobby

    /**
     * a location above the ground (the highest block at x/z of a location without a set height)
     */
    protected static Location safe(Location location) {
        try {
            int y = location.getWorld().getHighestBlockYAt(location);
            if (location.getY() <= y) {
                return new Location(location.getWorld(), location.getBlockX() + 0.5, y + 1, location.getBlockZ() + 0.5,
                        location.getYaw(), location.getPitch());
            }
        } catch (RuntimeException ignored) {
            // MockBukkit
        }
        return location;
    }

    /**
     * the lobby of the arena, otherwise the lobby world (while the players wait), otherwise the center
     */
    public Location getLobby() {
        Location lobby = arena.getLobby(getWorld());
        if (lobby != null) return lobby;
        World world = loadLobbyWorld();
        return world != null ? plugin.getArenaManager().getLobby().getSpawn(world) : safe(arena.getCenter(getWorld()));
    }

    /**
     * @return the copy of the lobby world of this game (loaded while the players wait), null if the arena has a lobby
     * of its own, there is no lobby world or the game has begun
     */
    @Nullable
    private World loadLobbyWorld() {
        if (lobbyWorld != null && lobbyWorld.getWorld() != null) return lobbyWorld.getWorld();
        if (stopped || state != GameState.LOBBY || !arena.usesLobbyWorld()) return null;
        try {
            String name = gameWorld.getWorldName();
            GameWorld copy = plugin.getArenaManager().getLobby().createWorld(name.substring(name.indexOf('_') + 1));
            this.lobbyWorld = copy;
            World world = copy.load();
            Lobby.setup(world);
            return world;
        } catch (RuntimeException ex) {
            Debug.warn("Couldn't load the lobby world for the game of arena '" + arena.getName() + "'", ex);
            return null;
        }
    }

    /**
     * the lobby world or the lobby of the arena has changed: a waiting game loads its copy again - unless players are
     * in it, they keep the old one until the game begins
     */
    public void reloadLobby() {
        World world = lobbyWorld == null ? null : lobbyWorld.getWorld();
        if (world != null && !world.getPlayers().isEmpty()) return;
        closeLobby();
        loadLobbyWorld();
    }

    /**
     * the copy of the lobby world isn't needed anymore (the game has begun or stops)
     */
    private void closeLobby() {
        if (lobbyWorld == null) return;
        World world = lobbyWorld.getWorld();
        if (world != null) {
            Location spawn = Bukkit.getWorlds().getFirst().getSpawnLocation();
            for (Player player : world.getPlayers()) {
                player.teleport(spawn);
            }
        }
        if (!lobbyWorld.unload()) Debug.warn("The lobby world of the game of arena '" + arena.getName() + "' stays loaded");
        this.lobbyWorld = null;
    }

    /**
     * the player waits in the lobby with the lobby items
     */
    protected void sendToLobby(Player player) {
        plugin.getUser(player).getStorage().cleanPlayer();
        player.setGameMode(GameMode.ADVENTURE);
        player.teleport(getLobby());
        giveLobbyItems(player);
    }

    protected void giveLobbyItems(Player player) {
        if (arena.getTeamSize() > 1) {
            player.getInventory().setItem(0, Items.create(Material.WHITE_BANNER, i18n(LangText.ITEM_TEAMS),
                    I.itemLines(LangText.ITEM_TEAMS_LORE), Items.TEAMS));
        }
        if (plugin.getKits().isUsed(this)) {
            player.getInventory().setItem(4, Items.create(Material.CHEST, i18n(LangText.ITEM_KITS),
                    I.itemLines(LangText.ITEM_KITS_LORE), Items.KITS));
        }
        player.getInventory().setItem(8, Items.create(Material.RED_BED, i18n(LangText.ITEM_LEAVE),
                I.itemLines(LangText.ITEM_LEAVE_LORE), Items.LEAVE));
    }

    /**
     * the players in the lobby move into the lobby of the other game (map voting) with their teams and kits - this
     * game is empty afterwards
     */
    void moveLobby(Game next) {
        next.participants.putAll(participants);
        for (GameTeam team : teams.getTeams()) {
            GameTeam copy = next.teams.create(team.getName(), team.getColor());
            if (copy == null) continue;
            team.getMembers().forEach(member -> next.teams.addMember(copy, member));
        }
        participants.clear();
        teams.clear();
        for (GamePlayer member : members) {
            members.remove(member);
            next.members.add(member);
            member.setGame(next);
            member.getPlayer().closeInventory();
            next.sendToLobby(member.getPlayer());
        }
        next.setCountdown(countdown);
        this.countdown = -1;
        plugin.updateVisibility();
    }

    /**
     * @return true if players may create teams and join them (in the lobby)
     */
    public boolean canChooseTeams() {
        return state == GameState.LOBBY && arena.getTeamSize() > 1;
    }

    /**
     * @return false if the team has as many players as the arena allows
     */
    public boolean hasRoom(GameTeam team) {
        return team.getMembers().size() < arena.getTeamSize();
    }

    /**
     * the player joins the team (the caller has checked that he may)
     */
    public void joinTeam(UUID player, GameTeam team) {
        GameTeam old = teams.getTeam(player);
        teams.addMember(team, player);
        if (old != null && old != team && old.getMembers().isEmpty()) plugin.getBackpack().delete(this, old);
        broadcast(LangText.TEAM_JOINED, getName(player), team.getDisplayName());
        teamsChanged();
    }

    /**
     * @return the old team, null if the player wasn't in one
     */
    @Nullable
    public GameTeam leaveTeam(UUID player) {
        GameTeam team = teams.removeMember(player);
        if (team == null) return null;
        if (team.getMembers().isEmpty()) plugin.getBackpack().delete(this, team);
        for (UUID member : team.getMembers()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) online.sendMessage(prefixed(LangText.TEAM_MEMBER_LEFT, getName(player)));
        }
        teamsChanged();
        return team;
    }

    public void deleteTeam(GameTeam team) {
        teams.delete(team);
        plugin.getBackpack().delete(this, team);
        teamsChanged();
    }

    /**
     * @return false if the player can't change his team now (he got a message why)
     */
    private boolean checkTeamChoice(Player player) {
        if (!canChooseTeams()) {
            player.sendMessage(prefixed(arena.getTeamSize() > 1 ? LangText.TEAMS_LOCKED : LangText.TEAMS_SOLO));
            return false;
        }
        if (!participants.containsKey(player.getUniqueId())) {
            player.sendMessage(prefixed(LangText.NOT_PLAYING));
            return false;
        }
        return true;
    }

    /**
     * the player joins the team (team menu, /sg team join)
     */
    public void chooseTeam(Player player, GameTeam team) {
        if (!checkTeamChoice(player)) return;
        if (team.isMember(player.getUniqueId())) {
            player.sendMessage(prefixed(LangText.ALREADY_IN_TEAM));
        } else if (!hasRoom(team)) {
            player.sendMessage(prefixed(LangText.TEAM_FULL));
        } else {
            joinTeam(player.getUniqueId(), team);
        }
    }

    /**
     * the player creates a team and joins it (/sg team create)
     *
     * @param color null: the next free color
     */
    public void createTeam(Player player, String name, @Nullable NamedTextColor color) {
        if (!checkTeamChoice(player)) return;
        if (!player.hasPermission(SurvivalGames.ADMIN_PERMISSION)
                && !plugin.getFileManager().getConfigFile().getBoolean("teams.players-can-create")) {
            player.sendMessage(prefixed(LangText.TEAM_CREATE_DISABLED));
            return;
        }
        if (!Teams.NAME.matcher(name).matches()) {
            player.sendMessage(prefixed(LangText.TEAM_NAME_INVALID));
            return;
        }
        GameTeam team = teams.create(name, color);
        if (team == null) {
            player.sendMessage(prefixed(LangText.TEAM_EXISTS, name));
            return;
        }
        broadcast(LangText.TEAM_CREATED, player.getName(), team.getDisplayName(), team.getName());
        plugin.getTelemetry().used(Telemetry.Feature.TEAM_CREATED);
        joinTeam(player.getUniqueId(), team);
    }

    /**
     * the player leaves his team (team menu, /sg team leave)
     */
    public void quitTeam(Player player) {
        if (!checkTeamChoice(player)) return;
        GameTeam team = leaveTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(prefixed(LangText.NOT_IN_TEAM));
        } else {
            player.sendMessage(prefixed(LangText.TEAM_LEFT, team.getDisplayName()));
        }
    }

    /**
     * @return true if an admin may put the player into a team (in the lobby: players of the game, in a Varo anybody)
     */
    protected boolean canRegister(UUID uuid) {
        return participants.containsKey(uuid);
    }

    /**
     * an admin puts the player into the team (/sg game &lt;arena&gt; team add)
     *
     * @return false if the game isn't in the lobby or the player can't take part
     */
    public boolean addToTeam(UUID uuid, String name, GameTeam team) {
        if (state != GameState.LOBBY || !canRegister(uuid)) return false;
        participate(uuid, name);
        joinTeam(uuid, team);
        return true;
    }

    /**
     * an admin creates a team (without members, it is deleted when the last member leaves it)
     *
     * @return null if the name is invalid or taken
     */
    @Nullable
    public GameTeam createTeam(String name, @Nullable NamedTextColor color) {
        GameTeam team = teams.create(name, color);
        if (team != null) teamsChanged();
        return team;
    }

    /**
     * the teams have changed (saved by a Varo)
     */
    protected void teamsChanged() {
    }

    // ---------------------------------------------------------------- start

    /**
     * @param force also with fewer players than min-players (at least one)
     * @return why the game can't start, null if it can
     */
    @Nullable
    public Component checkStart(boolean force) {
        if (stopped || state != GameState.LOBBY) return i18n(LangText.GAME_ALREADY_STARTED);
        int players = participants.size();
        if (players < 1 || !force && players < arena.getMinPlayers()) {
            return i18n(LangText.NOT_ENOUGH_PLAYERS, players, arena.getMinPlayers());
        }
        return null;
    }

    /**
     * puts every participant into a team and onto a spawn point, then the start countdown begins (check
     * {@link #checkStart} first)
     */
    public void start() {
        countdown = -1;
        fillTeams();
        assignSpawns();
        setState(GameState.STARTING);
        for (GamePlayer member : getPlayers()) {
            Participant participant = participants.get(member.getPlayer().getUniqueId());
            if (participant != null) placeOnSpawn(member.getPlayer(), participant);
        }
        onStart();
        int seconds = Math.max(0, settings().getInt("start-countdown"));
        if (seconds == 0) {
            begin();
        } else {
            setCountdown(seconds);
        }
    }

    /**
     * the game is starting (players are on the spawn points)
     */
    protected void onStart() {
    }

    /**
     * players without a team get one: alone with a team size of 1, otherwise teams are filled up. Parties
     * (parties.same-team) are placed first and together: into the team a member chose, otherwise into a team with
     * room for all of them.
     */
    protected void fillTeams() {
        int size = arena.getTeamSize();
        Map<UUID, UUID> partyOf = new HashMap<>();
        if (size > 1) {
            for (Participant participant : participants.values()) {
                Player player = participant.getPlayer();
                UUID party = player == null ? null : plugin.getPartySupport().getTeamParty(player);
                if (party != null) partyOf.put(participant.getUuid(), party);
            }
        }
        // players without a party form a group of their own
        Map<Object, List<Participant>> groups = new LinkedHashMap<>();
        for (Participant participant : participants.values()) {
            if (teams.getTeam(participant.getUuid()) != null) continue;
            UUID party = partyOf.get(participant.getUuid());
            groups.computeIfAbsent(party != null ? party : participant, key -> new ArrayList<>()).add(participant);
        }
        List<List<Participant>> ordered = new ArrayList<>(groups.values());
        // parties first (the biggest first) - otherwise players without a party take the places in the team a member
        // of the party chose
        ordered.sort(Comparator.comparing((List<Participant> group) -> !partyOf.containsKey(group.getFirst().getUuid()))
                .thenComparing(Comparator.comparingInt(List<Participant>::size).reversed()));
        for (List<Participant> group : ordered) {
            GameTeam team = chosenTeam(partyOf.get(group.getFirst().getUuid()), partyOf);
            for (int i = 0; i < group.size(); i++) {
                Participant participant = group.get(i);
                if (team == null || !hasRoom(team)) team = teamFor(participant, group.size() - i);
                if (team != null) teams.addMember(team, participant.getUuid());
            }
        }
        teamsChanged();
    }

    /**
     * @return the team a member of the party chose (if it has room), null if none
     */
    @Nullable
    private GameTeam chosenTeam(@Nullable UUID party, Map<UUID, UUID> partyOf) {
        if (party == null) return null;
        for (Map.Entry<UUID, UUID> entry : partyOf.entrySet()) {
            GameTeam team = entry.getValue().equals(party) ? teams.getTeam(entry.getKey()) : null;
            if (team != null && hasRoom(team)) return team;
        }
        return null;
    }

    /**
     * @param players the players who should play in the same team (the participant and the rest of his party)
     * @return the team the participant joins: the emptiest team with room for all of them, otherwise a new one
     */
    @Nullable
    private GameTeam teamFor(Participant participant, int players) {
        int size = arena.getTeamSize();
        if (size > 1) {
            GameTeam team = teams.getTeams().stream()
                    .filter(other -> size - other.getMembers().size() >= Math.min(players, size))
                    .min(Comparator.comparingInt(other -> other.getMembers().size())).orElse(null);
            if (team != null) return team;
        }
        NamedTextColor color = teams.nextColor();
        String name = size == 1 && Teams.NAME.matcher(participant.getName()).matches()
                && teams.get(participant.getName()) == null ? participant.getName() : teams.nextName(color);
        GameTeam team = teams.create(name, color);
        if (team != null || size == 1) return team;
        // no more names or colors: any team with room
        return teams.getTeams().stream().filter(this::hasRoom)
                .min(Comparator.comparingInt(other -> other.getMembers().size())).orElse(null);
    }

    /**
     * the spawn points: the ones of the arena, otherwise a circle around the center
     */
    protected List<Location> resolveSpawns(int count) {
        World world = getWorld();
        List<Location> arenaSpawns = arena.getSpawns(world);
        Location center = arena.getCenter(world);
        if (!arenaSpawns.isEmpty()) {
            for (Location spawn : arenaSpawns) {
                onTop(spawn);
                lookAt(spawn, center);
            }
            return arenaSpawns;
        }
        double radius = Math.max(2, settings().getDouble("spawn-circle-radius", 15));
        List<Location> circle = new ArrayList<>();
        for (int i = 0; i < Math.max(1, count); i++) {
            double angle = 2 * Math.PI * i / Math.max(1, count);
            Location location = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            location.setY(world.getMinHeight());
            Location spawn = safe(location);
            lookAt(spawn, center);
            circle.add(spawn);
        }
        return circle;
    }

    /**
     * a spawn point inside a block (older versions saved them at the bottom of a slab) is moved on top of it - otherwise the
     * player falls through
     */
    private static void onTop(Location location) {
        try {
            Block block = location.getBlock();
            if (block.isPassable()) return;
            double top = block.getBoundingBox().getMaxY();
            if (top > location.getY()) location.setY(top);
        } catch (RuntimeException ignored) {
            // MockBukkit
        }
    }

    /**
     * the location looks straight at the target (unless it is right on it)
     */
    private static void lookAt(Location location, Location target) {
        Vector direction = target.toVector().subtract(location.toVector()).setY(0);
        if (direction.lengthSquared() > 0.25) location.setDirection(direction);
    }

    /**
     * the members of a team get spawn points next to each other
     */
    protected void assignSpawns() {
        this.spawns = resolveSpawns(participants.size());
        int spawn = 0;
        for (GameTeam team : teams.getTeams()) {
            for (UUID member : team.getMembers()) {
                Participant participant = participants.get(member);
                if (participant == null) continue;
                participant.setSpawn(spawn++);
                participant.setStarted(false);
                participant.setAlive(true);
            }
        }
    }

    /**
     * @return the spawn point of the participant
     */
    protected Location getSpawn(Participant participant) {
        if (spawns.isEmpty()) this.spawns = resolveSpawns(Math.max(1, participants.size()));
        int index = Math.max(0, participant.getSpawn());
        return spawns.get(index % spawns.size());
    }

    /**
     * the player stands on the spawn point with an empty inventory
     */
    protected void placeOnSpawn(Player player, Participant participant) {
        plugin.getUser(player).getStorage().cleanPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(getSpawn(participant));
        participant.setStarted(true);
    }

    /**
     * the countdown is over: the game runs
     */
    protected void begin() {
        countdown = -1;
        setState(GameState.RUNNING);
        closeLobby();
        // the level showed the countdown
        for (GamePlayer member : members) {
            member.getPlayer().setLevel(0);
            member.getPlayer().setExp(0);
        }
        plugin.getReplays().begin(this);
        World world = getWorld();
        int size = getStartBorderSize();
        if (size > 0) {
            WorldBorder border = world.getWorldBorder();
            border.setCenter(arena.getCenter(world));
            border.setSize(size);
            border.setDamageAmount(Math.max(0, settings().getDouble("world-border.damage", 0.2)));
            border.setDamageBuffer(Math.max(0, settings().getDouble("world-border.buffer", 5)));
            border.setWarningDistance(10);
        }
        for (Participant participant : participants.values()) {
            plugin.getStats().add(participant.getUuid(), participant.getName(), getType(), Stat.GAMES);
            // the change at the end depends on the ratings of the opponents now
            participant.setRating(plugin.getStats().getRating(participant.getUuid(), getType()));
        }
        plugin.getKits().give(this);
        showTitle(Title.title(i18n(LangText.GAME_STARTED_TITLE), i18n(getType().getDisplayName())),
                Sound.ENTITY_ENDER_DRAGON_GROWL);
        onBegin();
    }

    /**
     * @return the size of the world border, 0 if there is none
     */
    public int getBorderSize() {
        World world = gameWorld.getWorld();
        if (world == null || state == GameState.LOBBY) return 0;
        double size = world.getWorldBorder().getSize();
        // the default border of a world (none set)
        return size >= 1_000_000 ? 0 : (int) Math.round(size);
    }

    /**
     * @return the size of the world border when the game begins: world-border.size, with world-border.per-team the
     * min-size plus per-team blocks for every team (at most world-border.size)
     */
    public int getStartBorderSize() {
        int size = arena.getBorderSize();
        int perTeam = settings().getInt("world-border.per-team");
        if (size <= 0 || perTeam <= 0) return size;
        int teams = (int) Math.ceil(participants.size() / (double) Math.max(1, arena.getTeamSize()));
        return Math.clamp(Math.max(0, settings().getInt("world-border.min-size")) + (long) perTeam * teams, 1, size);
    }

    /**
     * the players alive who play right now get a tracker (modules.tracker)
     */
    protected void giveTrackers() {
        for (GamePlayer member : getPlayers()) {
            Participant participant = participants.get(member.getPlayer().getUniqueId());
            if (participant != null && participant.isAlive()) plugin.getTracker().give(this, member.getPlayer());
        }
    }

    protected abstract void onBegin();

    /**
     * @return the seconds of the lobby or start countdown, -1 if there is none
     */
    public int getCountdown() {
        return countdown;
    }

    /**
     * starts (or shortens) the countdown, its first second is shown right away - -1 stops it
     */
    protected void setCountdown(int seconds) {
        this.countdown = seconds;
        if (seconds > 0) countdownTick();
    }

    // ---------------------------------------------------------------- running

    private void tick() {
        if (stopped) return;
        try {
            if (countdown > 0 && --countdown > 0) {
                countdownTick();
            } else if (countdown == 0) {
                countdown = -1;
                if (state == GameState.LOBBY) {
                    Component denial = checkStart(true);
                    if (denial == null) start();
                } else if (state == GameState.STARTING) {
                    begin();
                    // the first second of the game is the next one
                    return;
                }
            }
            onTick();
        } catch (RuntimeException ex) {
            Debug.warn("Error in the game of arena '" + arena.getName() + "'", ex);
        }
    }

    private void countdownTick() {
        boolean lobby = state == GameState.LOBBY;
        for (GamePlayer member : members) {
            member.getPlayer().setLevel(countdown);
        }
        if (!COUNTDOWN_MESSAGES.contains(countdown)) return;
        if (lobby) {
            broadcast(countdown == 1 ? LangText.LOBBY_COUNTDOWN_ONE : LangText.LOBBY_COUNTDOWN, countdown);
        } else {
            broadcast(countdown == 1 ? LangText.START_COUNTDOWN_ONE : LangText.START_COUNTDOWN, countdown);
        }
        if (countdown <= 5) {
            showTitle(Title.title(Component.text(countdown, NamedTextColor.YELLOW), Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO)), Sound.BLOCK_NOTE_BLOCK_PLING);
        }
    }

    /**
     * every second
     */
    protected void onTick() {
    }

    /**
     * a player of the game died
     */
    public void onDeath(GamePlayer gamePlayer, PlayerDeathEvent e) {
        Player player = gamePlayer.getPlayer();
        Participant participant = participants.get(player.getUniqueId());
        Player killer = player.getKiller();
        Participant killerParticipant = killer == null ? null : participants.get(killer.getUniqueId());
        e.deathMessage(null);
        if (killerParticipant != null && killer != player) {
            broadcast(LangText.PLAYER_KILLED, player.getName(), killer.getName());
            // before the items are dropped
            plugin.getInventoryView().onKill(this, player, killer);
        } else {
            broadcast(LangText.PLAYER_DIED, player.getName());
        }
        if (participant != null) eliminate(participant, killerParticipant == participant ? null : killerParticipant);
    }

    /**
     * after the respawn of a player who died in the game: watches or leaves the game
     */
    public void afterRespawn(GamePlayer gamePlayer, @Nullable Location deathLocation) {
        if (gamePlayer.getGame() != this) return;
        outOfPlay(gamePlayer, deathLocation);
    }

    /**
     * a player in the game who is out (died, strikes) watches or leaves the game
     *
     * @param location where he starts watching
     */
    protected void outOfPlay(GamePlayer gamePlayer, @Nullable Location location) {
        if (settings().getBoolean("spectators") && !stopped) {
            makeSpectator(gamePlayer, location);
        } else {
            kick(gamePlayer, prefixed(LangText.KICK_ELIMINATED));
        }
    }

    /**
     * the participant is out of the game (died, gave up)
     *
     * @param killer gets the kill (null: nobody)
     */
    public void eliminate(Participant participant, @Nullable Participant killer) {
        if (state != GameState.RUNNING || !participant.isAlive()) return;
        participant.setAlive(false);
        participant.setOut(++eliminations);
        plugin.getStats().add(participant.getUuid(), participant.getName(), getType(), Stat.DEATHS);
        if (killer != null && killer != participant) {
            killer.addKill();
            plugin.getStats().add(killer.getUuid(), killer.getName(), getType(), Stat.KILLS);
            plugin.getAchievements().onKill(this, killer);
            Rewards.give(plugin, this, killer.getUuid(), killer.getName(), EconomySupport.Reward.KILL);
        }
        plugin.getBounty().onEliminated(this, participant, killer);
        GameTeam team = teams.getTeam(participant.getUuid());
        if (arena.getTeamSize() == 1 || team == null) {
            broadcast(LangText.PLAYER_ELIMINATED_SOLO, participant.getName(), getAlive().size());
        } else {
            long teammates = getAlive().stream().filter(alive -> team.isMember(alive.getUuid())).count();
            if (teammates > 0) {
                broadcast(LangText.PLAYER_ELIMINATED, participant.getName(), team.getDisplayName(), teammates);
            } else {
                broadcast(LangText.TEAM_ELIMINATED, team.getDisplayName(), getAliveTeams().size());
            }
        }
        onEliminated(participant);
        checkWinner();
    }

    protected void onEliminated(Participant participant) {
    }

    /**
     * the player is back in the game (admins)
     */
    public void revive(Participant participant) {
        participant.setAlive(true);
        participant.setOut(0);
        onRevive(participant);
    }

    protected void onRevive(Participant participant) {
    }

    protected void checkWinner() {
        if (state != GameState.RUNNING) return;
        List<GameTeam> alive = getAliveTeams();
        if (alive.size() <= 1) end(alive.isEmpty() ? null : alive.getFirst());
    }

    protected void end(@Nullable GameTeam winner) {
        countdown = -1;
        plugin.getTelemetry().gamePlayed(this, winner == null);
        setState(GameState.ENDED);
        this.winner = winner == null ? null : winner.getName();
        Title title;
        if (winner == null) {
            broadcast(LangText.GAME_DRAW);
            title = Title.title(i18n(LangText.GAME_DRAW_TITLE), Component.empty());
        } else {
            for (UUID member : winner.getMembers()) {
                plugin.getStats().add(member, getName(member), getType(), Stat.WINS);
            }
            plugin.getAchievements().onWin(this, winner);
            String names = String.join(", ", winner.getMembers().stream().map(this::getName).toList());
            if (arena.getTeamSize() == 1) {
                broadcast(LangText.GAME_WON_SOLO, names, arena.getDisplayName());
                title = Title.title(i18n(LangText.GAME_WON_TITLE, Component.text(names, winner.getColor())), Component.empty());
            } else {
                broadcast(LangText.GAME_WON, winner.getDisplayName(), names, arena.getDisplayName());
                title = Title.title(i18n(LangText.GAME_WON_TITLE, winner.getDisplayName()), Component.text(names));
            }
        }
        showTitle(title, Sound.UI_TOAST_CHALLENGE_COMPLETE);
        if (winner != null) {
            for (UUID member : winner.getMembers()) {
                Rewards.give(plugin, this, member, getName(member), EconomySupport.Reward.WIN);
            }
            // the winners playing right now (in a Varo the others are offline)
            List<Player> inGame = getPlayers().stream().map(GamePlayer::getPlayer)
                    .filter(player -> winner.isMember(player.getUniqueId())).toList();
            plugin.getInventoryView().onEnd(this, inGame);
            Rewards.fireworks(plugin, this, inGame, winner.getColor());
        }
        for (Participant participant : participants.values()) {
            Rewards.give(plugin, this, participant.getUuid(), participant.getName(), EconomySupport.Reward.GAME);
        }
        rate(winner);
        // nobody has killed them
        plugin.getBounty().refundAll(this);
        onEnd();
    }

    /**
     * changes the Elo ratings of the participants by how long they (their team) lasted (stats.rating)
     */
    private void rate(@Nullable GameTeam winner) {
        if (!plugin.getStats().isRated(getType()) || participants.size() < 2) return;
        Map<UUID, Integer> scores = new HashMap<>();
        Map<UUID, Integer> ratings = new HashMap<>();
        for (Participant participant : participants.values()) {
            GameTeam team = teams.getTeam(participant.getUuid());
            int score;
            if (team == null) {
                score = participant.isAlive() ? Integer.MAX_VALUE : participant.getOut();
            } else if (team == winner) {
                score = Integer.MAX_VALUE;
            } else {
                // a team is out with its last player
                score = team.getMembers().stream().map(participants::get).filter(Objects::nonNull)
                        .mapToInt(member -> member.isAlive() ? Integer.MAX_VALUE : member.getOut()).max().orElse(0);
            }
            scores.put(participant.getUuid(), score);
            // 0: the game was loaded after a restart (Varo)
            ratings.put(participant.getUuid(), participant.getRating() > 0 ? participant.getRating()
                    : plugin.getStats().getRating(participant.getUuid(), getType()));
        }
        double factor = Math.max(1, plugin.getFileManager().getConfigFile().getData().getDouble("stats.rating.k-factor", 32));
        Rating.changes(scores, ratings, factor).forEach((uuid, change) -> {
            Participant participant = participants.get(uuid);
            plugin.getStats().add(uuid, participant.getName(), getType(), Stat.RATING, change);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(prefixed(LangText.RATING_CHANGED, ratings.get(uuid) + change,
                        Component.text((change >= 0 ? "+" : "") + change, change >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED)));
            }
        });
    }

    protected abstract void onEnd();

    /**
     * @return the name of the team that won, null if the game isn't over or nobody won
     */
    @Nullable
    public String getWinner() {
        return winner;
    }

    protected void setWinner(@Nullable String winner) {
        this.winner = winner;
    }

    // ---------------------------------------------------------------- stop

    /**
     * all players leave, the world is unloaded (a round of Survival Games deleted) - called by the
     * {@link GameManager}
     */
    void stop() {
        if (stopped) return;
        stopped = true;
        ticker.cancel();
        plugin.getReplays().stop(this);
        for (GamePlayer member : members) {
            leave(member, LeaveReason.STOP);
        }
        plugin.getBackpack().closeAll(this);
        onStop();
        World world = gameWorld.getWorld();
        if (world != null) {
            Location spawn = Bukkit.getWorlds().getFirst().getSpawnLocation();
            for (Player player : world.getPlayers()) {
                player.teleport(spawn);
            }
        }
        closeLobby();
        if (!gameWorld.unload()) Debug.warn("The world of the game of arena '" + arena.getName() + "' stays loaded");
    }

    /**
     * the game stops (save it)
     */
    protected void onStop() {
    }

}
