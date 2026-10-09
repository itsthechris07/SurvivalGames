package com.christian34.survivalgames.game;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.files.FileManager;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.user.GamePlayer;
import com.christian34.survivalgames.user.UserStorage;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * A Varo: runs for days in the same world, every player may only play a few minutes per day (the time only runs
 * while he is in the game). One death and he is out, the last team wins. When a player leaves the game, his state in
 * the game (inventory, location, ...) is kept and he gets his normal state back.
 * <p>
 * The game is saved in games/&lt;arena&gt;.yml and its world is kept, so it survives restarts until an admin resets it.
 *
 * @author Christian34
 */
public class VaroGame extends Game {
    private static final Set<Integer> TIME_WARNINGS = Set.of(300, 60, 30, 10, 5, 4, 3, 2, 1);
    private final File file;
    private Clock clock = Clock.systemDefaultZone();
    @Nullable
    private String startDay;
    private int borderDay;
    private int ticks;

    public VaroGame(SurvivalGames plugin, Arena arena) {
        super(plugin, arena, arena.createWorld(arena.getName(), true));
        this.file = getFile(arena);
        load();
    }

    public static File getFile(Arena arena) {
        return new File(FileManager.getGamesFolder(), arena.getName() + ".yml");
    }

    @Override
    public GameType getType() {
        return GameType.VARO;
    }

    @Override
    protected void setupWorld(World world) {
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRules.KEEP_INVENTORY, false);
    }

    // ---------------------------------------------------------------- saving

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        GameState saved;
        try {
            saved = GameState.valueOf(data.getString("state", GameState.LOBBY.name()));
        } catch (IllegalArgumentException ex) {
            saved = GameState.LOBBY;
        }
        // a countdown can't go on after a restart
        super.setState(saved == GameState.STARTING ? GameState.LOBBY : saved);
        this.startDay = data.getString("start-day");
        this.borderDay = data.getInt("border-day");
        setWinner(data.getString("winner"));
        teams.load(data.getConfigurationSection("teams"));
        ConfigurationSection players = data.getConfigurationSection("participants");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                ConfigurationSection section = players.getConfigurationSection(key);
                Participant participant = section == null ? null : Participant.load(key, section);
                if (participant != null) participants.put(participant.getUuid(), participant);
            }
        }
        ConfigurationSection backpacks = data.getConfigurationSection("backpacks");
        if (backpacks != null) {
            for (String key : backpacks.getKeys(false)) {
                String contents = backpacks.getString(key);
                if (contents != null) getBackpacks().put(key, contents);
            }
        }
    }

    /**
     * writes the game to games/&lt;arena&gt;.yml (every minute, on changes and when it stops)
     */
    public void save() {
        // the state of the players in the game right now (lost after a crash otherwise)
        for (GamePlayer member : getPlayers()) {
            Participant participant = getParticipant(member.getPlayer().getUniqueId());
            if (participant != null && participant.isAlive() && participant.isStarted()
                    && getState() == GameState.RUNNING) {
                participant.setState(UserStorage.snapshot(member.getPlayer()));
            }
        }
        YamlConfiguration data = new YamlConfiguration();
        data.set("arena", arena.getName());
        data.set("state", getState().name());
        data.set("start-day", startDay);
        data.set("border-day", borderDay);
        data.set("winner", getWinner());
        teams.save(data.createSection("teams"));
        ConfigurationSection players = data.createSection("participants");
        for (Participant participant : participants.values()) {
            participant.save(players.createSection(participant.getUuid().toString()));
        }
        ConfigurationSection backpacks = data.createSection("backpacks");
        getBackpacks().forEach(backpacks::set);
        try {
            File folder = file.getParentFile();
            if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Couldn't create " + folder);
            data.save(file);
        } catch (IOException ex) {
            Debug.warn("Couldn't save the Varo of arena '" + arena.getName() + "'", ex);
        }
    }

    @Override
    protected void setState(GameState state) {
        super.setState(state);
        if (file != null) save();
    }

    @Override
    protected void teamsChanged() {
        if (file != null) save();
    }

    /**
     * deletes the saved game and its world (the game is stopped)
     */
    void deleteData() {
        gameWorld.delete();
        if (file.isFile() && !file.delete()) Debug.warn("Couldn't delete " + file);
    }

    /**
     * the players and teams of a game that is reset take part again (alive, without kills and play time)
     */
    void takeOver(VaroGame old) {
        for (GameTeam team : old.teams.getTeams()) {
            GameTeam copy = teams.create(team.getName(), team.getColor());
            if (copy == null) continue;
            for (UUID member : team.getMembers()) {
                participate(member, old.getName(member));
                teams.addMember(copy, member);
            }
        }
        save();
    }

    // ---------------------------------------------------------------- days and play time

    /**
     * replaces the clock of the play time (the tests skip days)
     */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    private int getDayStartHour() {
        return Math.clamp(settings().getInt("day-start-hour"), 0, 23);
    }

    /**
     * @return the current day of the play time (a new day starts at day-start-hour), e.g. 2026-10-09
     */
    public String today() {
        return LocalDateTime.now(clock).minusHours(getDayStartHour()).toLocalDate().toString();
    }

    /**
     * @return the day of the game (1 on the day it started), 0 if it isn't running
     */
    public int getDay() {
        if (startDay == null || getState() != GameState.RUNNING) return 0;
        return (int) ChronoUnit.DAYS.between(LocalDate.parse(startDay), LocalDate.parse(today())) + 1;
    }

    /**
     * @return the time until a new day starts (and the play time is reset)
     */
    public Duration getTimeUntilNextDay() {
        LocalDateTime next = LocalDate.parse(today()).plusDays(1).atTime(getDayStartHour(), 0);
        return Duration.between(LocalDateTime.now(clock), next);
    }

    /**
     * the play time per day in seconds (play-time in minutes)
     */
    public int getPlayTime() {
        return Math.max(1, settings().getInt("play-time")) * 60;
    }

    /**
     * @return the seconds the player may still play today
     */
    public int getRemaining(Participant participant) {
        return Math.max(0, getPlayTime() - participant.getPlayed(today()));
    }

    // ---------------------------------------------------------------- joining and leaving

    @Override
    @Nullable
    public Component getJoinDenial(UUID uuid) {
        Participant participant = getParticipant(uuid);
        switch (getState()) {
            case LOBBY -> {
                if (participant == null && participants.size() >= getMaxPlayers()) return i18n(LangText.GAME_FULL);
                return null;
            }
            case STARTING, RUNNING -> {
                if (participant == null) return i18n(LangText.GAME_RUNNING, arena.getName());
                if (!participant.isAlive()) return i18n(LangText.KICK_ELIMINATED);
                if (getRemaining(participant) <= 0) {
                    Duration wait = getTimeUntilNextDay();
                    return i18n(LangText.KICK_NO_TIME_LEFT, wait.toHours(), wait.toMinutesPart());
                }
                return null;
            }
            default -> {
                return i18n(LangText.GAME_OVER);
            }
        }
    }

    @Override
    protected boolean canRegister(UUID uuid) {
        // also players who aren't online: they join when they come
        return participants.containsKey(uuid) || participants.size() < getMaxPlayers();
    }

    @Override
    public boolean isJoinable() {
        return !isStopped() && getState() == GameState.LOBBY && participants.size() < getMaxPlayers();
    }

    /**
     * players who are still in the game can't watch it (e.g. after their play time): they would scout the others
     */
    @Override
    public boolean maySpectate(UUID uuid) {
        Participant participant = getParticipant(uuid);
        return isSpectatable() && (participant == null || !participant.isAlive());
    }

    /**
     * @return true if the player could join the game now (a participant during the game)
     */
    public boolean canRejoin(UUID uuid) {
        return getState() != GameState.LOBBY && getParticipant(uuid) != null && getJoinDenial(uuid) == null;
    }

    @Override
    protected void onJoinRunning(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        Participant participant = participate(player.getUniqueId(), player.getName());
        if (!participant.isStarted()) {
            // offline at the start: onto his spawn point now
            placeOnSpawn(player, participant);
        } else if (participant.getState() != null) {
            UserStorage.apply(player, participant.getState(), false);
            player.setGameMode(GameMode.SURVIVAL);
        } else {
            placeOnSpawn(player, participant);
        }
        if (getState() == GameState.RUNNING) startSession(player, participant);
    }

    private void startSession(Player player, Participant participant) {
        int protection = Math.max(0, settings().getInt("join-protection"));
        participant.protect(protection);
        player.sendMessage(I.prefixed(LangText.SESSION_STARTED, formatTime(getRemaining(participant)), protection));
    }

    @Override
    protected void onLeave(GamePlayer gamePlayer, LeaveReason reason) {
        Player player = gamePlayer.getPlayer();
        Participant participant = getParticipant(player.getUniqueId());
        if (getState() == GameState.LOBBY) {
            // the teams stay for the start - only players without one don't take part anymore
            if (getTeam(player.getUniqueId()) == null) removeParticipant(player.getUniqueId());
            if (reason != LeaveReason.STOP) {
                broadcast(LangText.GAME_LEFT, player.getName(), participants.size(), getMaxPlayers());
            }
            return;
        }
        if (participant == null || !participant.isAlive() || getState() == GameState.ENDED) return;
        if (participant.isStarted()) participant.setState(UserStorage.snapshot(player));
        save();
    }

    /**
     * @return true if an enemy is near the player (he can't leave the game then)
     */
    public boolean isInCombat(Player player) {
        int radius = settings().getInt("combat-radius");
        GameTeam team = getTeam(player.getUniqueId());
        if (radius <= 0 || team == null || getState() != GameState.RUNNING) return false;
        for (GamePlayer member : getPlayers()) {
            Player other = member.getPlayer();
            if (other == player || team.isMember(other.getUniqueId()) || !other.getWorld().equals(player.getWorld())) {
                continue;
            }
            Participant participant = getParticipant(other.getUniqueId());
            if (participant == null || !participant.isAlive()) continue;
            if (other.getLocation().distanceSquared(player.getLocation()) <= (double) radius * radius) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- running

    @Override
    protected void onBegin() {
        this.startDay = today();
        this.borderDay = 1;
        World world = getWorld();
        world.setTime(0);
        world.setStorm(false);
        world.setThundering(false);
        broadcast(LangText.VARO_STARTED, formatTime(getPlayTime()));
        for (GamePlayer member : getPlayers()) {
            Participant participant = getParticipant(member.getPlayer().getUniqueId());
            if (participant != null) startSession(member.getPlayer(), participant);
        }
        save();
    }

    @Override
    protected void onTick() {
        if (getState() != GameState.RUNNING) return;
        if (++ticks % 60 == 0) save();
        checkNewDay();
        String today = today();
        int playTime = getPlayTime();
        for (GamePlayer member : getPlayers()) {
            Player player = member.getPlayer();
            Participant participant = getParticipant(player.getUniqueId());
            if (participant == null || !participant.isAlive() || player.isDead()) continue;
            int played = participant.getPlayed(today) + 1;
            participant.setPlayed(today, played);
            int remaining = playTime - played;
            if (remaining > 0) {
                if (TIME_WARNINGS.contains(remaining)) {
                    player.sendMessage(I.prefixed(LangText.TIME_WARNING, formatTime(remaining)));
                    if (remaining <= 10) player.playSound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                }
                player.sendActionBar(i18n(LangText.ACTIONBAR_TIME_LEFT, formatTime(remaining)));
            } else if (isInCombat(player)) {
                // no escaping from a fight
                player.sendActionBar(i18n(LangText.ACTIONBAR_ENEMY_NEARBY));
            } else {
                broadcast(LangText.PLAYER_TIME_OVER, player.getName());
                Duration wait = getTimeUntilNextDay();
                kick(member, I.prefixed(LangText.KICK_TIME_OVER, wait.toHours(), wait.toMinutesPart()));
            }
        }
    }

    /**
     * a new day of the game: the world border shrinks
     */
    private void checkNewDay() {
        int day = getDay();
        if (day <= borderDay) return;
        this.borderDay = day;
        broadcast(LangText.NEW_DAY, day);
        save();
        World world = getWorld();
        int size = arena.getBorderSize();
        int shrink = settings().getInt("world-border.shrink-per-day");
        if (size <= 0 || shrink <= 0) return;
        int newSize = Math.max(settings().getInt("world-border.min-size"), size - shrink * (day - 1));
        if (newSize >= world.getWorldBorder().getSize()) return;
        world.getWorldBorder().changeSize(newSize, 20L * 60 * 10);
        broadcast(LangText.BORDER_SHRINKS, newSize);
    }

    @Override
    protected void onEliminated(Participant participant) {
        // a revived player starts on his spawn point again
        participant.setState(null);
        save();
    }

    @Override
    protected void onRevive(Participant participant) {
        participant.setState(null);
        participant.setStarted(false);
        save();
        Player player = participant.getPlayer();
        if (player == null) return;
        GamePlayer gamePlayer = plugin.getUser(player);
        if (gamePlayer.getGame() != this || !gamePlayer.isSpectator() || getState() != GameState.RUNNING) return;
        gamePlayer.setSpectator(false);
        placeOnSpawn(player, participant);
        startSession(player, participant);
        plugin.updateVisibility();
    }

    @Override
    protected void onEnd() {
        save();
    }

    @Override
    protected void onStop() {
        save();
    }

}
