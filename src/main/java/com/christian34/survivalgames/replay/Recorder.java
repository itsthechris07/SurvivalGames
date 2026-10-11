package com.christian34.survivalgames.replay;

import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.user.GamePlayer;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.entity.Trident;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records a game: every tick it looks at the players who play (where they are, their pose, what they hold and wear,
 * their health), at the projectiles on their way and at the blocks that were changed; hits, deaths and messages come
 * from the events ({@link Replays}). The ticks of the recording only count while somebody plays - in a Varo the players
 * come and go all day.
 *
 * @author Christian34
 */
final class Recorder {
    /**
     * moves smaller than this aren't recorded (blocks squared, degrees)
     */
    private static final double MIN_MOVE_SQUARED = 0.01 * 0.01;
    private static final float MIN_TURN = 1f;
    private final Game game;
    private final Recording recording;
    private final Map<UUID, Tracked> tracked = new HashMap<>();
    /**
     * the blocks that were changed since the last tick with their data before
     */
    private final Map<Block, BlockData> changedBlocks = new LinkedHashMap<>();
    private final Map<Projectile, Prop> props = new LinkedHashMap<>();
    private int tick;
    private int nextProp;
    private boolean idle = true;
    private boolean dirty;
    private double borderX;
    private double borderZ;
    private double borderSize = -1;
    private long skyTime = -1;
    private boolean storm;

    /**
     * what was recorded of a player the last time
     */
    private static final class Tracked {
        private final int actor;
        private final ItemStack[] equipment = new ItemStack[Recording.SLOTS.length];
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;
        @Nullable
        private String pose;
        private boolean burning;
        private boolean invisible;
        private float health = -1;

        Tracked(int actor) {
            this.actor = actor;
        }

        boolean hasMoved(Location location) {
            double dx = location.getX() - x;
            double dy = location.getY() - y;
            double dz = location.getZ() - z;
            return dx * dx + dy * dy + dz * dz > MIN_MOVE_SQUARED || Math.abs(location.getYaw() - yaw) > MIN_TURN
                    || Math.abs(location.getPitch() - pitch) > MIN_TURN;
        }

        void set(Location location) {
            this.x = location.getX();
            this.y = location.getY();
            this.z = location.getZ();
            this.yaw = location.getYaw();
            this.pitch = location.getPitch();
        }
    }

    private static final class Prop {
        private final int id;
        private double x;
        private double y;
        private double z;

        Prop(int id, Location location) {
            this.id = id;
            this.x = location.getX();
            this.y = location.getY();
            this.z = location.getZ();
        }
    }

    /**
     * @param recording a new recording or the one of today that goes on (a Varo after a restart)
     */
    Recorder(Game game, Recording recording) {
        this.game = game;
        this.recording = recording;
        this.tick = recording.getTicks();
    }

    Game getGame() {
        return game;
    }

    Recording getRecording() {
        return recording;
    }

    /**
     * @return true if something was recorded since the last {@link #saved()}
     */
    boolean isDirty() {
        return dirty;
    }

    void saved() {
        this.dirty = false;
    }

    private void add(Action action) {
        recording.getActions().add(action);
        this.dirty = true;
    }

    /**
     * @return true while somebody plays (the rest of the time nothing is recorded)
     */
    boolean isActive() {
        return !idle;
    }

    // ---------------------------------------------------------------- every tick

    void tick() {
        List<Player> players = getPlayers();
        if (players.isEmpty() && tracked.isEmpty() && props.isEmpty()) {
            changedBlocks.clear();
            this.idle = true;
            return;
        }
        recording.setTicks(++tick);
        if (idle) {
            this.idle = false;
            add(new Action.Clock(tick, System.currentTimeMillis()));
            // again, they may have changed in the meantime
            this.borderSize = -1;
            this.skyTime = -1;
        }
        Map<UUID, Tracked> gone = new HashMap<>(tracked);
        for (Player player : players) {
            gone.remove(player.getUniqueId());
            record(player);
        }
        gone.forEach((uuid, left) -> {
            tracked.remove(uuid);
            Participant participant = game.getParticipant(uuid);
            // out of the game without dying (e.g. the death was cancelled by another plugin)
            add(new Action.Despawn(tick, left.actor, participant != null && !participant.isAlive()));
        });
        recordBlocks();
        recordProps();
        if (tick % 20 == 1) recordWorld();
    }

    /**
     * @return the players who play right now
     */
    private List<Player> getPlayers() {
        GameState state = game.getState();
        World world = game.getGameWorld().getWorld();
        if (world == null || state != GameState.RUNNING && state != GameState.ENDED) return List.of();
        List<Player> players = new ArrayList<>();
        for (GamePlayer member : game.getPlayers()) {
            Player player = member.getPlayer();
            Participant participant = game.getParticipant(player.getUniqueId());
            if (participant == null || player.isDead() || !world.equals(player.getWorld())) continue;
            // after the end the winners celebrate
            if (state == GameState.RUNNING && !participant.isAlive()) continue;
            players.add(player);
        }
        return players;
    }

    private void record(Player player) {
        Location location = player.getLocation();
        Tracked state = tracked.get(player.getUniqueId());
        if (state == null) {
            state = new Tracked(recording.actor(actor(player)));
            tracked.put(player.getUniqueId(), state);
            state.set(location);
            add(new Action.Spawn(tick, state.actor, location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch()));
        } else if (state.hasMoved(location)) {
            state.set(location);
            add(new Action.Move(tick, state.actor, location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch()));
        }
        String pose = player.getPose().name();
        boolean burning = player.getFireTicks() > 0;
        boolean invisible = player.isInvisible() || player.hasPotionEffect(PotionEffectType.INVISIBILITY);
        if (!pose.equals(state.pose) || burning != state.burning || invisible != state.invisible) {
            state.pose = pose;
            state.burning = burning;
            state.invisible = invisible;
            add(new Action.Pose(tick, state.actor, pose, burning, invisible));
        }
        EntityEquipment equipment = player.getEquipment();
        for (int slot = 0; slot < Recording.SLOTS.length; slot++) {
            ItemStack item = equipment.getItem(Recording.SLOTS[slot]);
            if (looksLike(item, state.equipment[slot])) continue;
            boolean empty = item == null || item.isEmpty();
            state.equipment[slot] = empty ? null : item.clone();
            add(new Action.Equip(tick, state.actor, slot, empty ? null : serialize(item)));
        }
        float health = (float) player.getHealth();
        if (Math.abs(health - state.health) >= 0.5f) {
            state.health = health;
            add(new Action.Health(tick, state.actor, health));
        }
    }

    private Recording.Actor actor(Player player) {
        GameTeam team = game.getTeam(player.getUniqueId());
        String skin = null;
        String signature = null;
        for (ProfileProperty property : player.getPlayerProfile().getProperties()) {
            if (!property.getName().equals("textures")) continue;
            skin = property.getValue();
            signature = property.getSignature();
        }
        return new Recording.Actor(player.getUniqueId(), player.getName(), team == null ? null : team.getName(),
                team == null ? null : NamedTextColor.NAMES.key(team.getColor()), skin, signature);
    }

    /**
     * @return true if both items look the same in the hand or as armor (the durability and the amount change all the
     * time)
     */
    private static boolean looksLike(@Nullable ItemStack item, @Nullable ItemStack other) {
        boolean empty = item == null || item.isEmpty();
        boolean otherEmpty = other == null || other.isEmpty();
        if (empty || otherEmpty) return empty == otherEmpty;
        return item.getType() == other.getType() && item.getEnchantments().isEmpty() == other.getEnchantments().isEmpty();
    }

    static byte[] serialize(ItemStack item) {
        return ItemStack.serializeItemsAsBytes(new ItemStack[]{item});
    }

    @Nullable
    static ItemStack deserialize(byte @Nullable [] item) {
        if (item == null || item.length == 0) return null;
        ItemStack[] items = ItemStack.deserializeItemsFromBytes(item);
        return items.length == 0 || items[0] == null || items[0].isEmpty() ? null : items[0];
    }

    private void recordBlocks() {
        if (changedBlocks.isEmpty()) return;
        changedBlocks.forEach((block, before) -> {
            BlockData after = block.getBlockData();
            if (after.equals(before)) return;
            add(new Action.SetBlock(tick, block.getX(), block.getY(), block.getZ(), before.getAsString().intern(),
                    after.getAsString().intern()));
        });
        changedBlocks.clear();
    }

    private void recordProps() {
        Iterator<Map.Entry<Projectile, Prop>> iterator = props.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Projectile, Prop> entry = iterator.next();
            Prop prop = entry.getValue();
            if (!entry.getKey().isValid()) {
                iterator.remove();
                add(new Action.PropRemove(tick, prop.id));
                continue;
            }
            Location location = entry.getKey().getLocation();
            double dx = location.getX() - prop.x;
            double dy = location.getY() - prop.y;
            double dz = location.getZ() - prop.z;
            if (dx * dx + dy * dy + dz * dz <= MIN_MOVE_SQUARED) continue;
            prop.x = location.getX();
            prop.y = location.getY();
            prop.z = location.getZ();
            add(new Action.PropMove(tick, prop.id, prop.x, prop.y, prop.z));
        }
    }

    /**
     * the world border, the time of day and the weather (every second)
     */
    private void recordWorld() {
        World world = game.getWorld();
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double size = border.getSize();
        if (Math.abs(size - borderSize) > 0.01 || center.getX() != borderX || center.getZ() != borderZ) {
            this.borderSize = size;
            this.borderX = center.getX();
            this.borderZ = center.getZ();
            add(new Action.Border(tick, borderX, borderZ, size));
        }
        long time = world.getTime();
        boolean storm = world.hasStorm();
        // the sun moves slowly: every 5 seconds is enough
        if (skyTime < 0 || storm != this.storm || Math.abs(time - skyTime) >= 100) {
            this.skyTime = time;
            this.storm = storm;
            add(new Action.Sky(tick, time, storm));
        }
    }

    // ---------------------------------------------------------------- events

    void swing(Player player, boolean offHand) {
        Tracked state = tracked.get(player.getUniqueId());
        if (state != null) add(new Action.Swing(tick, state.actor, offHand));
    }

    void hurt(Player player) {
        Tracked state = tracked.get(player.getUniqueId());
        if (state != null) add(new Action.Hurt(tick, state.actor));
    }

    void death(Player player) {
        Tracked state = tracked.remove(player.getUniqueId());
        if (state != null) add(new Action.Despawn(tick, state.actor, true));
    }

    /**
     * the block is about to change (or has just been changed)
     *
     * @param before its data before the change
     */
    void block(Block block, BlockData before) {
        if (!idle) changedBlocks.putIfAbsent(block, before);
    }

    void message(Component message) {
        if (!idle) add(new Action.Message(tick, GsonComponentSerializer.gson().serialize(message)));
    }

    void lightning(Location location) {
        if (!idle) add(new Action.Lightning(tick, location.getX(), location.getY(), location.getZ()));
    }

    /**
     * a player of the game has shot or thrown something
     */
    void launch(Player shooter, Projectile projectile) {
        ItemStack item = getItem(projectile);
        if (item == null || !tracked.containsKey(shooter.getUniqueId())) return;
        Location location = projectile.getLocation();
        Prop prop = new Prop(nextProp++, location);
        props.put(projectile, prop);
        add(new Action.PropSpawn(tick, prop.id, serialize(item), prop.x, prop.y, prop.z));
    }

    /**
     * @return the item that is shown for the projectile in the replay, null: it isn't recorded
     */
    @Nullable
    private static ItemStack getItem(Projectile projectile) {
        return switch (projectile) {
            case Trident ignored -> ItemStack.of(Material.TRIDENT);
            case ThrowableProjectile thrown -> thrown.getItem();
            case SpectralArrow ignored -> ItemStack.of(Material.SPECTRAL_ARROW);
            case AbstractArrow ignored -> ItemStack.of(Material.ARROW);
            case Firework ignored -> ItemStack.of(Material.FIREWORK_ROCKET);
            default -> null;
        };
    }

    /**
     * the recording ends here (the game is over or stops, a new day of a Varo begins)
     */
    void finish() {
        tracked.forEach((uuid, state) -> add(new Action.Despawn(tick, state.actor, false)));
        tracked.clear();
        props.values().forEach(prop -> add(new Action.PropRemove(tick, prop.id)));
        props.clear();
        changedBlocks.clear();
        this.idle = true;
        if (game.getWinner() != null) recording.setWinner(game.getWinner());
    }

}
