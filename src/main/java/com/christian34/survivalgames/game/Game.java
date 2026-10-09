package com.christian34.survivalgames.game;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.arena.GameWorld;
import com.christian34.survivalgames.lib.Items;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.user.GamePlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
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
    @Nullable
    private String winner;
    private List<Location> spawns = List.of();
    /**
     * the contents of the backpacks (modules.backpack) by team or player
     */
    private final Map<String, String> backpacks = new ConcurrentHashMap<>();

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
    }

    public void broadcast(LangText text, Object... arguments) {
        broadcast(I.prefixed(text, arguments));
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
            player.sendMessage(I.prefixed(denial));
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
            gamePlayer.getPlayer().sendMessage(I.prefixed(LangText.GAME_NOT_SPECTATABLE));
            return false;
        }
        enter(gamePlayer, true);
        makeSpectator(gamePlayer, null);
        plugin.updateVisibility();
        return true;
    }

    private void enter(GamePlayer gamePlayer, boolean spectator) {
        Player player = gamePlayer.getPlayer();
        if (plugin.getBuildMode().isBuilding(player)) plugin.getBuildMode().toggle(player);
        gamePlayer.getStorage().store();
        gamePlayer.getStorage().cleanPlayer();
        gamePlayer.setGame(this);
        gamePlayer.setSpectator(spectator);
        members.add(gamePlayer);
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
        player.sendMessage(I.prefixed(LangText.SPECTATING));
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
        if (player.getWorld().equals(gameWorld.getWorld())) {
            // no saved location (e.g. a crash) - out of the game world
            player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
        }
        plugin.updateVisibility();
        if (reason == LeaveReason.COMMAND) player.sendMessage(I.prefixed(LangText.GAME_LEFT_SELF, arena.getDisplayName()));
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
     * the lobby of the arena, otherwise the center
     */
    public Location getLobby() {
        Location lobby = arena.getLobby(getWorld());
        return lobby != null ? lobby : safe(arena.getCenter(getWorld()));
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
        player.getInventory().setItem(8, Items.create(Material.RED_BED, i18n(LangText.ITEM_LEAVE),
                I.itemLines(LangText.ITEM_LEAVE_LORE), Items.LEAVE));
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
            if (online != null) online.sendMessage(I.prefixed(LangText.TEAM_MEMBER_LEFT, getName(player)));
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
            player.sendMessage(I.prefixed(arena.getTeamSize() > 1 ? LangText.TEAMS_LOCKED : LangText.TEAMS_SOLO));
            return false;
        }
        if (!participants.containsKey(player.getUniqueId())) {
            player.sendMessage(I.prefixed(LangText.NOT_PLAYING));
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
            player.sendMessage(I.prefixed(LangText.ALREADY_IN_TEAM));
        } else if (!hasRoom(team)) {
            player.sendMessage(I.prefixed(LangText.TEAM_FULL));
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
            player.sendMessage(I.prefixed(LangText.TEAM_CREATE_DISABLED));
            return;
        }
        if (!Teams.NAME.matcher(name).matches()) {
            player.sendMessage(I.prefixed(LangText.TEAM_NAME_INVALID));
            return;
        }
        GameTeam team = teams.create(name, color);
        if (team == null) {
            player.sendMessage(I.prefixed(LangText.TEAM_EXISTS, name));
            return;
        }
        broadcast(LangText.TEAM_CREATED, player.getName(), team.getDisplayName(), team.getName());
        joinTeam(player.getUniqueId(), team);
    }

    /**
     * the player leaves his team (team menu, /sg team leave)
     */
    public void quitTeam(Player player) {
        if (!checkTeamChoice(player)) return;
        GameTeam team = leaveTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(I.prefixed(LangText.NOT_IN_TEAM));
        } else {
            player.sendMessage(I.prefixed(LangText.TEAM_LEFT, team.getDisplayName()));
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
     * players without a team get one: alone with a team size of 1, otherwise teams are filled up
     */
    protected void fillTeams() {
        int size = arena.getTeamSize();
        for (Participant participant : participants.values()) {
            if (teams.getTeam(participant.getUuid()) != null) continue;
            GameTeam team = null;
            if (size > 1) {
                team = teams.getTeams().stream().filter(this::hasRoom)
                        .min((a, b) -> Integer.compare(a.getMembers().size(), b.getMembers().size())).orElse(null);
            }
            if (team == null) {
                NamedTextColor color = teams.nextColor();
                String name = size == 1 && Teams.NAME.matcher(participant.getName()).matches()
                        && teams.get(participant.getName()) == null ? participant.getName() : teams.nextName(color);
                team = teams.create(name, color);
            }
            if (team != null) teams.addMember(team, participant.getUuid());
        }
        teamsChanged();
    }

    /**
     * the spawn points: the ones of the arena, otherwise a circle around the center
     */
    protected List<Location> resolveSpawns(int count) {
        World world = getWorld();
        List<Location> arenaSpawns = arena.getSpawns(world);
        if (!arenaSpawns.isEmpty()) return arenaSpawns;
        Location center = arena.getCenter(world);
        double radius = Math.max(2, settings().getDouble("spawn-circle-radius", 15));
        List<Location> circle = new ArrayList<>();
        for (int i = 0; i < Math.max(1, count); i++) {
            double angle = 2 * Math.PI * i / Math.max(1, count);
            Location location = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            location.setY(world.getMinHeight());
            Location spawn = safe(location);
            // facing the center
            spawn.setDirection(center.toVector().subtract(spawn.toVector()));
            circle.add(spawn);
        }
        return circle;
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
        World world = getWorld();
        int size = arena.getBorderSize();
        if (size > 0) {
            WorldBorder border = world.getWorldBorder();
            border.setCenter(arena.getCenter(world));
            border.setSize(size);
        }
        showTitle(Title.title(i18n(LangText.GAME_STARTED_TITLE), i18n(getType().getDisplayName())),
                Sound.ENTITY_ENDER_DRAGON_GROWL);
        onBegin();
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
        if (settings().getBoolean("spectators") && !stopped) {
            makeSpectator(gamePlayer, deathLocation);
        } else {
            kick(gamePlayer, I.prefixed(LangText.KICK_ELIMINATED));
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
        if (killer != null && killer != participant) killer.addKill();
        GameTeam team = teams.getTeam(participant.getUuid());
        if (arena.getTeamSize() == 1 || team == null) {
            broadcast(LangText.PLAYER_ELIMINATED_SOLO, participant.getName(), getAlive().size());
        } else {
            broadcast(LangText.PLAYER_ELIMINATED, participant.getName(), team.getDisplayName(), getAliveTeams().size());
            if (!isAlive(team)) broadcast(LangText.TEAM_ELIMINATED, team.getDisplayName());
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
        setState(GameState.ENDED);
        this.winner = winner == null ? null : winner.getName();
        Title title;
        if (winner == null) {
            broadcast(LangText.GAME_DRAW);
            title = Title.title(i18n(LangText.GAME_DRAW_TITLE), Component.empty());
        } else {
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
        onEnd();
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
        if (!gameWorld.unload()) Debug.warn("The world of the game of arena '" + arena.getName() + "' stays loaded");
    }

    /**
     * the game stops (save it)
     */
    protected void onStop() {
    }

}
