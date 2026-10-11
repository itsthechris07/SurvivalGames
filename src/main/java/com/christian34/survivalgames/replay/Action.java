package com.christian34.survivalgames.replay;

import org.jetbrains.annotations.Nullable;

/**
 * Something that happened in a recorded game. The tick is the time since the recording began (20 per second, only
 * counted while somebody played).
 *
 * @author Christian34
 */
public sealed interface Action {

    int tick();

    /**
     * the player is in the game (it began, he joined a Varo)
     */
    record Spawn(int tick, int actor, double x, double y, double z, float yaw, float pitch) implements Action {
    }

    record Move(int tick, int actor, double x, double y, double z, float yaw, float pitch) implements Action {
    }

    /**
     * the player isn't in the game anymore
     *
     * @param dead true: he died, false: he left
     */
    record Despawn(int tick, int actor, boolean dead) implements Action {
    }

    /**
     * @param pose the name of the {@link org.bukkit.entity.Pose}
     */
    record Pose(int tick, int actor, String pose, boolean burning, boolean invisible) implements Action {
    }

    /**
     * @param slot the index in {@link Recording#SLOTS}
     * @param item the serialized item, null: nothing
     */
    record Equip(int tick, int actor, int slot, byte @Nullable [] item) implements Action {
    }

    record Swing(int tick, int actor, boolean offHand) implements Action {
    }

    record Hurt(int tick, int actor) implements Action {
    }

    record Health(int tick, int actor, float health) implements Action {
    }

    /**
     * @param before the block data before the change (to jump back)
     */
    record SetBlock(int tick, int x, int y, int z, String before, String after) implements Action {
    }

    /**
     * a message to everybody in the game (kills, deathmatch, ...)
     *
     * @param json the component
     */
    record Message(int tick, String json) implements Action {
    }

    /**
     * a projectile (arrow, snowball, ...) is on its way
     *
     * @param item the serialized item that is shown for it
     */
    record PropSpawn(int tick, int prop, byte[] item, double x, double y, double z) implements Action {
    }

    record PropMove(int tick, int prop, double x, double y, double z) implements Action {
    }

    record PropRemove(int tick, int prop) implements Action {
    }

    /**
     * the center and the size of the world border as they are now
     */
    record Border(int tick, double x, double z, double size) implements Action {
    }

    /**
     * the time of day and the weather
     */
    record Sky(int tick, long time, boolean storm) implements Action {
    }

    /**
     * the real time (the ticks don't count while nobody plays a Varo)
     *
     * @param millis milliseconds since 1970
     */
    record Clock(int tick, long millis) implements Action {
    }

    record Lightning(int tick, double x, double y, double z) implements Action {
    }

}
