package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.UsageStats;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.user.GamePlayer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gamemaker events (modules.arena-events): every few minutes (min-minutes to max-minutes, after the grace period, not
 * in the deathmatch) the arena turns against the players for duration seconds, announced warning seconds before:
 * <ul>
 *     <li>poison-water: the water poisons</li>
 *     <li>swarm: angry mobs (swarm-mob) attack everybody</li>
 *     <li>fog: nobody sees far</li>
 *     <li>heat-wave: the sun makes hungry and slow under the open sky</li>
 * </ul>
 *
 * @author Christian34
 */
public class ArenaEvents extends GameModule {
    public static final String MODULE = "arena-events";

    public enum Event {
        POISON_WATER(LangText.ARENA_EVENT_POISON_WATER),
        SWARM(LangText.ARENA_EVENT_SWARM),
        FOG(LangText.ARENA_EVENT_FOG),
        HEAT_WAVE(LangText.ARENA_EVENT_HEAT_WAVE);

        private final LangText text;

        Event(LangText text) {
            this.text = text;
        }

        public String getKey() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        @Nullable
        public static Event parse(String name) {
            for (Event event : values()) {
                if (event.getKey().equalsIgnoreCase(name.trim())) return event;
            }
            return null;
        }
    }

    private static final class State {
        /**
         * the second of the game the next event is announced, -1: not planned yet
         */
        int next = -1;
        @Nullable
        Event event;
        /**
         * the second it begins / ends
         */
        int begins;
        int ends;
        final List<Entity> mobs = new ArrayList<>();
    }

    public ArenaEvents(SurvivalGames plugin) {
        super(plugin, MODULE);
    }

    /**
     * @return the event going on in the game right now, null if there is none
     */
    @Nullable
    public Event getEvent(Game game) {
        State state = game.getModuleData(this, State.class);
        return state == null || state.event == null || game.getRunningSeconds() < state.begins ? null : state.event;
    }

    /**
     * every second of a running game
     */
    @Override
    public void tick(Game game) {
        State state = game.getModuleData(this, State.class, State::new);
        int seconds = game.getRunningSeconds();
        boolean paused = !game.isPvpAllowed() || game.isFinalPhase();
        if (state.event != null) {
            if (seconds >= state.ends || game.isFinalPhase()) {
                stop(game, state);
            } else if (seconds >= state.begins) {
                if (seconds == state.begins) begin(game, state);
                apply(game, state.event);
            }
            return;
        }
        if (paused) return;
        if (state.next < 0) {
            int min = Math.max(1, config().getInt("min-minutes", 3));
            int max = Math.max(min, config().getInt("max-minutes", 6));
            state.next = seconds + 60 * ThreadLocalRandom.current().nextInt(min, max + 1);
        }
        if (seconds >= state.next) start(game, state, null);
    }

    /**
     * the event is announced (it begins after warning seconds)
     *
     * @param event null: one of the events of the config
     * @return false if there is no event to start
     */
    public boolean start(Game game, @Nullable Event event) {
        if (game.getState() != GameState.RUNNING) return false;
        State state = game.getModuleData(this, State.class, State::new);
        if (state.event != null) stop(game, state);
        return start(game, state, event);
    }

    private boolean start(Game game, State state, @Nullable Event event) {
        state.next = -1;
        if (event == null) {
            List<Event> events = config().getStringList("events").stream().map(Event::parse)
                    .filter(Objects::nonNull).toList();
            if (events.isEmpty()) return false;
            event = events.get(ThreadLocalRandom.current().nextInt(events.size()));
        }
        int warning = Math.max(0, config().getInt("warning", 10));
        state.event = event;
        state.begins = game.getRunningSeconds() + warning;
        state.ends = state.begins + Math.max(1, config().getInt("duration", 30));
        plugin.getUsageStats().used(UsageStats.Feature.ARENA_EVENT);
        game.broadcast(LangText.ARENA_EVENT_WARNING, warning);
        if (warning == 0) {
            begin(game, state);
            apply(game, event);
        }
        return true;
    }

    private void begin(Game game, State state) {
        Event event = state.event;
        if (event == null) return;
        game.broadcast(event.text, Math.max(1, state.ends - state.begins));
        for (GamePlayer member : game.getMembers()) {
            member.getPlayer().playSound(member.getPlayer(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 1f);
        }
        if (event == Event.SWARM) spawnSwarm(game, state);
    }

    private void stop(Game game, State state) {
        clearMobs(state);
        state.event = null;
        if (game.getState() == GameState.RUNNING) game.broadcast(LangText.ARENA_EVENT_OVER);
    }

    @Override
    public void stop(Game game) {
        State state = game.getModuleData(this, State.class);
        if (state != null) clearMobs(state);
    }

    private static void clearMobs(State state) {
        state.mobs.forEach(Entity::remove);
        state.mobs.clear();
    }

    /**
     * the players alive who play right now
     */
    private static List<Player> victims(Game game) {
        List<Player> players = new ArrayList<>();
        for (GamePlayer member : game.getPlayers()) {
            Player player = member.getPlayer();
            Participant participant = game.getParticipant(player.getUniqueId());
            if (participant == null || !participant.isAlive()) continue;
            if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) continue;
            players.add(player);
        }
        return players;
    }

    /**
     * every second of the event
     */
    private void apply(Game game, Event event) {
        for (Player player : victims(game)) {
            switch (event) {
                case POISON_WATER -> {
                    if (isInWater(player)) player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1));
                }
                case FOG -> player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false));
                case HEAT_WAVE -> {
                    if (isUnderOpenSky(player.getLocation())) {
                        player.setExhaustion(player.getExhaustion() + 1.5f);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false));
                    }
                }
                case SWARM -> {
                }
            }
        }
    }

    private static boolean isInWater(Player player) {
        if (player.isInWater()) return true;
        Material type = player.getLocation().getBlock().getType();
        return type == Material.WATER || type == Material.BUBBLE_COLUMN;
    }

    private static boolean isUnderOpenSky(Location location) {
        return location.getWorld().getHighestBlockYAt(location) <= location.getBlockY();
    }

    private void spawnSwarm(Game game, State state) {
        EntityType type = EntityType.SILVERFISH;
        String name = config().getString("swarm-mob");
        if (name != null) {
            try {
                type = EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // the default
            }
        }
        int size = Math.clamp(config().getInt("swarm-size", 3), 1, 10);
        for (Player player : victims(game)) {
            for (int i = 0; i < size; i++) {
                Location location = player.getLocation().add(ThreadLocalRandom.current().nextDouble(-4, 4), 1,
                        ThreadLocalRandom.current().nextDouble(-4, 4));
                try {
                    Entity entity = player.getWorld().spawnEntity(location, type);
                    if (entity instanceof Mob mob) mob.setTarget(player);
                    entity.setPersistent(false);
                    state.mobs.add(entity);
                } catch (RuntimeException ex) {
                    // not a mob that can be spawned
                    return;
                }
            }
        }
    }

}
