package com.christian34.survivalgames.replay;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What the viewers of a replay see in its world: a mannequin with the skin, the name and the equipment of every player,
 * an item for every projectile, the blocks, the world border and the sky. The chunks the mannequins are in stay loaded,
 * so they are there before a viewer comes.
 *
 * @author Christian34
 */
final class Stage {
    private final SurvivalGames plugin;
    private final World world;
    private final List<Recording.Actor> actors;
    private final Map<Integer, Mannequin> mannequins = new HashMap<>();
    private final Map<Integer, ItemDisplay> props = new HashMap<>();
    /**
     * the chunks kept loaded: chunk key -> the mannequins in it
     */
    private final Map<Long, Integer> tickets = new HashMap<>();
    private boolean turnsBody = true;

    /**
     * a player at one moment of the recording
     */
    static final class ActorState {
        final byte[][] equipment = new byte[Recording.SLOTS.length][];
        boolean present;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        String pose = Pose.STANDING.name();
        boolean burning;
        boolean invisible;
        float health = 20;

        void reset() {
            this.present = false;
            this.pose = Pose.STANDING.name();
            this.burning = false;
            this.invisible = false;
            this.health = 20;
            java.util.Arrays.fill(equipment, null);
        }
    }

    /**
     * a projectile at one moment of the recording
     */
    record PropState(byte[] item, double x, double y, double z) {
    }

    Stage(SurvivalGames plugin, World world, List<Recording.Actor> actors) {
        this.plugin = plugin;
        this.world = world;
        this.actors = actors;
    }

    World getWorld() {
        return world;
    }

    /**
     * @return the mannequin of the player, null if he isn't in the game right now
     */
    @Nullable
    Mannequin getMannequin(int actor) {
        Mannequin mannequin = mannequins.get(actor);
        return mannequin != null && mannequin.isValid() ? mannequin : null;
    }

    private Location location(ActorState state) {
        return new Location(world, state.x, state.y, state.z, state.yaw, state.pitch);
    }

    // ---------------------------------------------------------------- players

    /**
     * the mannequin of the player is where he is and looks like him (spawned if it isn't there)
     */
    void show(int actor, ActorState state) {
        Location location = location(state);
        Mannequin mannequin = getMannequin(actor);
        if (mannequin == null) {
            mannequin = spawn(actor, location);
            if (mannequin == null) return;
            mannequins.put(actor, mannequin);
            keepLoaded(location, 1);
        } else {
            move(actor, state);
        }
        pose(actor, state);
        for (int slot = 0; slot < Recording.SLOTS.length; slot++) {
            equip(actor, slot, state.equipment[slot]);
        }
        health(actor, state.health);
    }

    @Nullable
    private Mannequin spawn(int actor, Location location) {
        Recording.Actor player = actors.get(actor);
        try {
            return world.spawn(location, Mannequin.class, spawned -> {
                try {
                    spawned.setProfile(profile(player));
                } catch (RuntimeException ex) {
                    // textures the server doesn't accept (and MockBukkit): the default skin
                }
                spawned.customName(name(player));
                spawned.setCustomNameVisible(true);
                spawned.setImmovable(true);
                spawned.setInvulnerable(true);
                spawned.setGravity(false);
                spawned.setCollidable(false);
                spawned.setSilent(true);
                // gone with the world, also after a crash
                spawned.setPersistent(false);
            });
        } catch (RuntimeException ex) {
            Debug.warn("Couldn't spawn " + player.name() + " in the replay", ex);
            return null;
        }
    }

    private static ResolvableProfile profile(Recording.Actor actor) {
        ResolvableProfile.Builder profile = ResolvableProfile.resolvableProfile().name(actor.name());
        if (actor.skin() != null) {
            // complete: the client doesn't look the skin up
            profile.uuid(actor.uuid()).addProperty(new ProfileProperty("textures", actor.skin(), actor.signature()));
        }
        return profile.build();
    }

    private static Component name(Recording.Actor actor) {
        NamedTextColor color = actor.color() == null ? null : NamedTextColor.NAMES.value(actor.color());
        return Component.text(actor.name(), color == null ? NamedTextColor.WHITE : color);
    }

    void move(int actor, ActorState state) {
        Mannequin mannequin = getMannequin(actor);
        if (mannequin == null) {
            // unloaded with its chunk or removed by somebody
            if (mannequins.remove(actor) != null) show(actor, state);
            return;
        }
        Location from = mannequin.getLocation();
        Location to = location(state);
        if (chunk(from) != chunk(to)) {
            keepLoaded(to, 1);
            keepLoaded(from, -1);
        }
        mannequin.teleport(to);
        if (!turnsBody) return;
        try {
            // otherwise the body only follows the head slowly
            mannequin.setBodyYaw(state.yaw);
        } catch (RuntimeException ex) {
            // MockBukkit lacks it
            this.turnsBody = false;
        }
    }

    /**
     * the player has left the game or died
     */
    void hide(int actor, boolean dead) {
        Mannequin mannequin = mannequins.remove(actor);
        if (mannequin == null) return;
        Location location = mannequin.getLocation();
        keepLoaded(location, -1);
        if (dead && mannequin.isValid()) {
            world.spawnParticle(Particle.POOF, location.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
            world.playSound(location, Sound.ENTITY_PLAYER_DEATH, 1f, 1f);
        }
        mannequin.remove();
    }

    void pose(int actor, ActorState state) {
        Mannequin mannequin = getMannequin(actor);
        if (mannequin == null) return;
        Pose pose;
        try {
            pose = Pose.valueOf(state.pose);
        } catch (IllegalArgumentException ex) {
            pose = Pose.STANDING;
        }
        if (!Mannequin.validPoses().contains(pose)) pose = Pose.STANDING;
        mannequin.setPose(pose, true);
        mannequin.setSneaking(pose == Pose.SNEAKING);
        mannequin.setVisualFire(state.burning ? TriState.TRUE : TriState.NOT_SET);
        mannequin.setInvisible(state.invisible);
    }

    void equip(int actor, int slot, byte @Nullable [] item) {
        Mannequin mannequin = getMannequin(actor);
        if (mannequin == null || slot < 0 || slot >= Recording.SLOTS.length) return;
        EntityEquipment equipment = mannequin.getEquipment();
        ItemStack stack = null;
        try {
            stack = Recorder.deserialize(item);
        } catch (RuntimeException ex) {
            // an item of a newer version
        }
        equipment.setItem(Recording.SLOTS[slot], stack, true);
    }

    void swing(int actor, boolean offHand) {
        Mannequin mannequin = getMannequin(actor);
        if (mannequin == null) return;
        if (offHand) {
            mannequin.swingOffHand();
        } else {
            mannequin.swingMainHand();
        }
    }

    void hurt(int actor) {
        Mannequin mannequin = getMannequin(actor);
        if (mannequin == null) return;
        mannequin.playHurtAnimation(0);
        world.playSound(mannequin.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
    }

    /**
     * the hearts of the player below his name
     */
    void health(int actor, float health) {
        Mannequin mannequin = getMannequin(actor);
        if (mannequin == null) return;
        mannequin.setDescription(Component.text(String.format(Locale.ROOT, "%.1f❤", health / 2), NamedTextColor.RED));
    }

    // ---------------------------------------------------------------- projectiles

    void showProp(int prop, PropState state) {
        Location location = new Location(world, state.x(), state.y(), state.z());
        ItemDisplay display = props.get(prop);
        if (display != null && display.isValid()) {
            display.teleport(location);
            return;
        }
        ItemStack item;
        try {
            item = Recorder.deserialize(state.item());
        } catch (RuntimeException ex) {
            item = null;
        }
        if (item == null) return;
        ItemStack shown = item;
        try {
            props.put(prop, world.spawn(location, ItemDisplay.class, spawned -> {
                spawned.setItemStack(shown);
                spawned.setPersistent(false);
                // moves smoothly from tick to tick and always faces the viewer, like a thrown item
                spawned.setTeleportDuration(1);
                spawned.setBillboard(Display.Billboard.CENTER);
            }));
        } catch (RuntimeException ex) {
            Debug.warn("Couldn't show a projectile in the replay", ex);
        }
    }

    void hideProp(int prop) {
        ItemDisplay display = props.remove(prop);
        if (display != null) display.remove();
    }

    /**
     * removes the projectiles that aren't on their way at the time the replay has jumped to
     */
    void retainProps(Set<Integer> shown) {
        props.entrySet().removeIf(entry -> {
            if (shown.contains(entry.getKey())) return false;
            entry.getValue().remove();
            return true;
        });
    }

    // ---------------------------------------------------------------- world

    /**
     * @param data the block data as text (physics don't apply: the neighbors are recorded on their own)
     */
    void block(int x, int y, int z, String data) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return;
        try {
            Block block = world.getBlockAt(x, y, z);
            block.setBlockData(Bukkit.createBlockData(data), false);
        } catch (IllegalArgumentException ex) {
            // a block of a newer version
        }
    }

    /**
     * @param smooth true: the border moves there within a second (the recording has its size once per second)
     */
    void border(double x, double z, double size, boolean smooth) {
        WorldBorder border = world.getWorldBorder();
        border.setCenter(x, z);
        if (smooth && Math.abs(border.getSize() - size) < 50) {
            border.changeSize(size, 20L);
        } else {
            border.setSize(size);
        }
    }

    void sky(long time, boolean storm) {
        world.setTime(time);
        world.setStorm(storm);
    }

    void lightning(double x, double y, double z) {
        world.strikeLightningEffect(new Location(world, x, y, z));
    }

    // ---------------------------------------------------------------- chunks

    private static long chunk(Location location) {
        return ((long) (location.getBlockX() >> 4) << 32) | ((location.getBlockZ() >> 4) & 0xFFFFFFFFL);
    }

    /**
     * @param change 1: a mannequin has come into the chunk, -1: one has left it
     */
    private void keepLoaded(Location location, int change) {
        long key = chunk(location);
        int x = location.getBlockX() >> 4;
        int z = location.getBlockZ() >> 4;
        int count = tickets.merge(key, change, Integer::sum);
        try {
            if (count <= 0) {
                tickets.remove(key);
                world.removePluginChunkTicket(x, z, plugin);
            } else if (count == 1 && change > 0) {
                world.addPluginChunkTicket(x, z, plugin);
            }
        } catch (RuntimeException ex) {
            // the mannequin is spawned again when its chunk is gone
        }
    }

    /**
     * removes everything (the world is unloaded or the replay jumps to another time)
     */
    void clear() {
        mannequins.values().forEach(Entity::remove);
        mannequins.clear();
        props.values().forEach(Entity::remove);
        props.clear();
        try {
            world.removePluginChunkTickets(plugin);
        } catch (RuntimeException ex) {
            // gone with the world
        }
        tickets.clear();
    }

}
