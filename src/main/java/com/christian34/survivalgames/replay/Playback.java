package com.christian34.survivalgames.replay;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.GameWorld;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A recording that is played in its own world: the viewers are spectators there and control it together (pause, speed,
 * jump to a time, watch through the eyes of a player).
 *
 * @author Christian34
 */
public final class Playback {
    public static final List<Double> SPEEDS = List.of(0.25, 0.5, 1.0, 2.0, 4.0, 8.0);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private final Recording recording;
    private final Recording.Info info;
    private final GameWorld gameWorld;
    private final Stage stage;
    private final List<Action> actions;
    private final Stage.ActorState[] states;
    private final Map<Integer, Stage.PropState> props = new LinkedHashMap<>();
    private final List<Player> viewers = new ArrayList<>();
    /**
     * viewer -> the player he watches through the eyes of
     */
    private final Map<UUID, Integer> following = new HashMap<>();
    /**
     * the next action
     */
    private int index;
    private int tick;
    private double budget;
    private double speed = 1;
    private boolean paused;
    private boolean ended;
    private int age;
    private double borderX;
    private double borderZ;
    private double borderSize = -1;
    private long skyTime = -1;
    private boolean storm;
    /**
     * the real time at {@link #clockTick}, 0: unknown
     */
    private long clock;
    private int clockTick;

    Playback(SurvivalGames plugin, Recording recording, GameWorld gameWorld, World world) {
        this.recording = recording;
        this.info = recording.getInfo();
        this.gameWorld = gameWorld;
        this.actions = recording.getActions();
        this.stage = new Stage(plugin, world, recording.getActors());
        this.states = new Stage.ActorState[recording.getActors().size()];
        for (int i = 0; i < states.length; i++) {
            states[i] = new Stage.ActorState();
        }
        // the players stand on their spawns
        seek(Math.min(1, getLength()));
    }

    public Recording.Info getInfo() {
        return info;
    }

    public String getId() {
        return info.id();
    }

    GameWorld getGameWorld() {
        return gameWorld;
    }

    public World getWorld() {
        return stage.getWorld();
    }

    public List<Player> getViewers() {
        return viewers;
    }

    /**
     * @return the time of the recording in ticks
     */
    public int getTick() {
        return tick;
    }

    public int getLength() {
        return recording.getTicks();
    }

    public boolean isPaused() {
        return paused;
    }

    public double getSpeed() {
        return speed;
    }

    // ---------------------------------------------------------------- viewers

    void add(Player player) {
        if (!viewers.contains(player)) viewers.add(player);
        player.teleport(getStart());
    }

    void remove(Player player) {
        viewers.remove(player);
        if (following.remove(player.getUniqueId()) != null && player.isOnline()) player.setSpectatorTarget(null);
    }

    /**
     * @return above the first player who is in the game right now, else above the center of the arena
     */
    private Location getStart() {
        for (Stage.ActorState state : states) {
            if (state.present) return new Location(getWorld(), state.x, state.y + 3, state.z, state.yaw, 30);
        }
        return new Location(getWorld(), info.x(), info.y() + 3, info.z());
    }

    private void send(Component message) {
        for (Player viewer : viewers) {
            viewer.sendMessage(message);
        }
    }

    // ---------------------------------------------------------------- playing

    /**
     * every tick of the server
     */
    void tick() {
        age++;
        if (!paused) {
            budget += speed;
            while (budget >= 1 && tick < getLength()) {
                budget--;
                tick++;
                while (index < actions.size() && actions.get(index).tick() <= tick) {
                    apply(actions.get(index++), true);
                }
            }
            if (tick >= getLength() && !ended) {
                this.ended = true;
                this.paused = true;
                this.budget = 0;
                send(I.prefixed(LangText.REPLAY_ENDED));
            }
        }
        follow();
        if (age % 10 == 0) showTime();
    }

    private void showTime() {
        Component bar = paused ? I.i18n(LangText.REPLAY_ACTIONBAR_PAUSED, getTimeText(), Game.formatTime(getLength() / 20))
                : I.i18n(LangText.REPLAY_ACTIONBAR, getTimeText(), Game.formatTime(getLength() / 20), formatSpeed(speed));
        for (Player viewer : viewers) {
            viewer.sendActionBar(bar);
        }
    }

    /**
     * @return e.g. 03:12, in a Varo with the time of day it was: 03:12 (18:45)
     */
    public String getTimeText() {
        String time = Game.formatTime(tick / 20);
        if (info.type() != GameType.VARO || clock <= 0) return time;
        return time + " (" + CLOCK.format(Instant.ofEpochMilli(clock + (tick - clockTick) * 50L)) + ")";
    }

    public static String formatSpeed(double speed) {
        return speed == Math.rint(speed) ? String.valueOf((int) speed) : String.valueOf(speed);
    }

    @Nullable
    private Stage.ActorState state(int actor) {
        return actor >= 0 && actor < states.length ? states[actor] : null;
    }

    /**
     * @param live true: the viewers see it happen, false: only what is left of it after a jump
     */
    private void apply(Action action, boolean live) {
        switch (action) {
            case Action.Spawn a -> {
                Stage.ActorState state = state(a.actor());
                if (state == null) return;
                state.present = true;
                set(state, a.x(), a.y(), a.z(), a.yaw(), a.pitch());
                if (live) stage.show(a.actor(), state);
            }
            case Action.Move a -> {
                Stage.ActorState state = state(a.actor());
                if (state == null) return;
                set(state, a.x(), a.y(), a.z(), a.yaw(), a.pitch());
                if (live && state.present) stage.move(a.actor(), state);
            }
            case Action.Despawn a -> {
                Stage.ActorState state = state(a.actor());
                if (state == null) return;
                // when he comes back, everything he has is recorded again
                state.reset();
                if (!live) return;
                stage.hide(a.actor(), a.dead());
                following.values().removeIf(actor -> actor == a.actor());
            }
            case Action.Pose a -> {
                Stage.ActorState state = state(a.actor());
                if (state == null) return;
                state.pose = a.pose();
                state.burning = a.burning();
                state.invisible = a.invisible();
                if (live) stage.pose(a.actor(), state);
            }
            case Action.Equip a -> {
                Stage.ActorState state = state(a.actor());
                if (state == null || a.slot() < 0 || a.slot() >= state.equipment.length) return;
                state.equipment[a.slot()] = a.item();
                if (live) stage.equip(a.actor(), a.slot(), a.item());
            }
            case Action.Swing a -> {
                if (live) stage.swing(a.actor(), a.offHand());
            }
            case Action.Hurt a -> {
                if (live) stage.hurt(a.actor());
            }
            case Action.Health a -> {
                Stage.ActorState state = state(a.actor());
                if (state == null) return;
                state.health = a.health();
                if (live) stage.health(a.actor(), a.health());
            }
            // the world has to be as it was, also after a jump
            case Action.SetBlock a -> stage.block(a.x(), a.y(), a.z(), a.after());
            case Action.Message a -> {
                if (!live) return;
                try {
                    send(GsonComponentSerializer.gson().deserialize(a.json()));
                } catch (RuntimeException ex) {
                    // a message of a newer version
                }
            }
            case Action.PropSpawn a -> {
                Stage.PropState prop = new Stage.PropState(a.item(), a.x(), a.y(), a.z());
                props.put(a.prop(), prop);
                if (live) stage.showProp(a.prop(), prop);
            }
            case Action.PropMove a -> {
                Stage.PropState old = props.get(a.prop());
                if (old == null) return;
                Stage.PropState prop = new Stage.PropState(old.item(), a.x(), a.y(), a.z());
                props.put(a.prop(), prop);
                if (live) stage.showProp(a.prop(), prop);
            }
            case Action.PropRemove a -> {
                props.remove(a.prop());
                if (live) stage.hideProp(a.prop());
            }
            case Action.Border a -> {
                this.borderX = a.x();
                this.borderZ = a.z();
                this.borderSize = a.size();
                if (live) stage.border(borderX, borderZ, borderSize, true);
            }
            case Action.Sky a -> {
                this.skyTime = a.time();
                this.storm = a.storm();
                if (live) stage.sky(skyTime, storm);
            }
            case Action.Clock a -> {
                this.clock = a.millis();
                this.clockTick = a.tick();
            }
            case Action.Lightning a -> {
                if (live) stage.lightning(a.x(), a.y(), a.z());
            }
        }
    }

    private static void set(Stage.ActorState state, double x, double y, double z, float yaw, float pitch) {
        state.x = x;
        state.y = y;
        state.z = z;
        state.yaw = yaw;
        state.pitch = pitch;
    }

    // ---------------------------------------------------------------- controls

    public void setPaused(boolean paused) {
        // again from the beginning
        if (!paused && tick >= getLength()) seek(0);
        this.paused = paused;
        showTime();
    }

    /**
     * @param speed one of {@link #SPEEDS}
     */
    public void setSpeed(double speed) {
        this.speed = speed;
        showTime();
    }

    /**
     * @return the next faster (or slower) speed, the same if there is none
     */
    public double getNextSpeed(boolean faster) {
        int current = SPEEDS.indexOf(speed);
        if (current < 0) return 1;
        return SPEEDS.get(Math.clamp(current + (faster ? 1 : -1), 0, SPEEDS.size() - 1));
    }

    /**
     * jumps to a time of the recording: the world, the players and the projectiles are as they were then
     */
    public void seek(int target) {
        target = Math.clamp(target, 0, getLength());
        if (target < tick) {
            while (index > 0 && actions.get(index - 1).tick() > target) {
                index--;
                if (actions.get(index) instanceof Action.SetBlock block) {
                    stage.block(block.x(), block.y(), block.z(), block.before());
                }
            }
            for (Stage.ActorState state : states) {
                state.reset();
            }
            props.clear();
            this.clock = 0;
            this.borderSize = -1;
            this.skyTime = -1;
            // the blocks are right already
            for (int i = 0; i < index; i++) {
                if (!(actions.get(i) instanceof Action.SetBlock)) apply(actions.get(i), false);
            }
        } else {
            while (index < actions.size() && actions.get(index).tick() <= target) {
                apply(actions.get(index++), false);
            }
        }
        this.tick = target;
        this.budget = 0;
        this.ended = false;
        sync();
        showTime();
    }

    /**
     * the stage shows what is known of this moment
     */
    private void sync() {
        for (int actor = 0; actor < states.length; actor++) {
            if (states[actor].present) {
                stage.show(actor, states[actor]);
            } else {
                stage.hide(actor, false);
            }
        }
        stage.retainProps(props.keySet());
        props.forEach(stage::showProp);
        if (borderSize > 0) stage.border(borderX, borderZ, borderSize, false);
        if (skyTime >= 0) stage.sky(skyTime, storm);
        int finalActors = states.length;
        following.values().removeIf(actor -> actor >= finalActors || !states[actor].present);
    }

    // ---------------------------------------------------------------- following

    /**
     * @return the players who are in the game at this moment of the recording
     */
    public List<Recording.Actor> getPresent() {
        List<Recording.Actor> present = new ArrayList<>();
        for (int actor = 0; actor < states.length; actor++) {
            if (states[actor].present) present.add(recording.getActors().get(actor));
        }
        return present;
    }

    /**
     * the viewer watches through the eyes of the player (until he sneaks)
     *
     * @return false if the player isn't in the game at this moment of the recording
     */
    public boolean follow(Player viewer, String name) {
        for (int actor = 0; actor < states.length; actor++) {
            if (!states[actor].present || !recording.getActors().get(actor).name().equalsIgnoreCase(name)) continue;
            Mannequin mannequin = stage.getMannequin(actor);
            if (mannequin == null) return false;
            following.put(viewer.getUniqueId(), actor);
            viewer.setSpectatorTarget(mannequin);
            return true;
        }
        return false;
    }

    /**
     * the viewer has left the body he watched from
     */
    void stopFollowing(Player viewer) {
        following.remove(viewer.getUniqueId());
    }

    /**
     * the mannequins are new after a jump: the viewers get back into them
     */
    private void follow() {
        if (following.isEmpty()) return;
        for (Player viewer : viewers) {
            Integer actor = following.get(viewer.getUniqueId());
            if (actor == null) continue;
            Mannequin mannequin = stage.getMannequin(actor);
            if (mannequin != null && !mannequin.equals(viewer.getSpectatorTarget())) viewer.setSpectatorTarget(mannequin);
        }
    }

    /**
     * removes the mannequins (the world is unloaded next)
     */
    void close() {
        following.clear();
        stage.clear();
    }

}
