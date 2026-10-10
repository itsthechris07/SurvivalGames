package com.christian34.survivalgames.game;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A player who takes part in a game (in a team): alive or out, kills, the spawn point and - in a Varo - the play time
 * of the current day, the strikes and the state of the player in the game while he isn't in it.
 *
 * @author Christian34
 */
public class Participant {
    private final UUID uuid;
    private String name;
    private boolean alive = true;
    private int kills;
    private int rating;
    private int out;
    @Nullable
    private String kit;
    /**
     * seconds played on {@link #day}
     */
    private int played;
    @Nullable
    private String day;
    /**
     * index of the spawn point, -1: none yet
     */
    private int spawn = -1;
    /**
     * true once the player was on the spawn point (in a Varo players who are offline at the start get there when they
     * join the game the first time)
     */
    private boolean started;
    /**
     * not saved: until then the player can't get damage after joining
     */
    private long protectedUntil;
    /**
     * Varo: inventory, location, health, ... in the game while the player isn't in it (null: never left it)
     */
    @Nullable
    private YamlConfiguration state;
    /**
     * Varo: the strikes of the admins, the first one first
     */
    private final List<Strike> strikes = new ArrayList<>();

    /**
     * a strike of an admin (Varo)
     *
     * @param by   the name of the admin
     * @param time when (milliseconds since 1970)
     */
    public record Strike(String reason, String by, long time) {
    }

    public Participant(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Nullable
    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public int getKills() {
        return kills;
    }

    public void addKill() {
        kills++;
    }

    /**
     * @return the Elo rating when the game began (not saved)
     */
    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    /**
     * @return when he was out of the game (1: as the first one, 2: as the second one, ...), 0 while he is alive (not
     * saved)
     */
    public int getOut() {
        return out;
    }

    public void setOut(int out) {
        this.out = out;
    }

    /**
     * @return the kit the player chose in the lobby (modules.kits), null: none
     */
    @Nullable
    public String getKit() {
        return kit;
    }

    public void setKit(@Nullable String kit) {
        this.kit = kit;
    }

    /**
     * @return the seconds played on the given day (0 on a new day)
     */
    public int getPlayed(String today) {
        return today.equals(day) ? played : 0;
    }

    /**
     * @return the last day the participant played (e.g. 2026-10-09), null if he hasn't played yet
     */
    @Nullable
    public String getLastDay() {
        return day;
    }

    public void setPlayed(String today, int played) {
        this.day = today;
        this.played = played;
    }

    public int getSpawn() {
        return spawn;
    }

    public void setSpawn(int spawn) {
        this.spawn = spawn;
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public boolean isProtected() {
        return System.currentTimeMillis() < protectedUntil;
    }

    public void protect(int seconds) {
        this.protectedUntil = System.currentTimeMillis() + seconds * 1000L;
    }

    @Nullable
    public YamlConfiguration getState() {
        return state;
    }

    public void setState(@Nullable YamlConfiguration state) {
        this.state = state;
    }

    public List<Strike> getStrikes() {
        return List.copyOf(strikes);
    }

    /**
     * @return the number of the strike (1 for the first one)
     */
    public int addStrike(Strike strike) {
        strikes.add(strike);
        return strikes.size();
    }

    /**
     * @return the strike that has been removed (the last one), null if there was none
     */
    @Nullable
    public Strike removeLastStrike() {
        return strikes.isEmpty() ? null : strikes.removeLast();
    }

    public void save(ConfigurationSection section) {
        section.set("name", name);
        section.set("alive", alive);
        section.set("kills", kills);
        section.set("day", day);
        section.set("played", played);
        section.set("spawn", spawn);
        section.set("started", started);
        section.set("state", state == null ? null : state.saveToString());
        List<Map<String, Object>> list = new ArrayList<>();
        for (Strike strike : strikes) {
            Map<String, Object> map = new HashMap<>();
            map.put("reason", strike.reason());
            map.put("by", strike.by());
            map.put("time", strike.time());
            list.add(map);
        }
        section.set("strikes", list.isEmpty() ? null : list);
    }

    @Nullable
    public static Participant load(String key, ConfigurationSection section) {
        UUID uuid;
        try {
            uuid = UUID.fromString(key);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        Participant participant = new Participant(uuid, section.getString("name", key));
        participant.alive = section.getBoolean("alive", true);
        participant.kills = section.getInt("kills");
        participant.day = section.getString("day");
        participant.played = section.getInt("played");
        participant.spawn = section.getInt("spawn", -1);
        participant.started = section.getBoolean("started");
        for (Map<?, ?> map : section.getMapList("strikes")) {
            participant.strikes.add(new Strike(String.valueOf(map.get("reason")), String.valueOf(map.get("by")),
                    map.get("time") instanceof Number time ? time.longValue() : 0));
        }
        String state = section.getString("state");
        if (state != null) {
            YamlConfiguration data = new YamlConfiguration();
            try {
                data.loadFromString(state);
                participant.state = data;
            } catch (InvalidConfigurationException ignored) {
            }
        }
        return participant;
    }

}
