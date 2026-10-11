package com.christian34.survivalgames.replay;

import com.christian34.survivalgames.game.GameType;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A recorded game: who played and what happened, tick by tick. A round of Survival Games is one recording, a Varo has
 * one per day. Saved as replays/&lt;id&gt;.sgr (gzip): the header ({@link Info}) first, so the list of the recordings
 * doesn't read the whole files.
 *
 * @author Christian34
 */
public final class Recording {
    public static final String EXTENSION = ".sgr";
    /**
     * the equipment of the players that is recorded
     */
    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.FEET,
            EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final int MAGIC = 0x53475231;
    private static final int VERSION = 1;
    private final Info info;
    private final List<Actor> actors = new ArrayList<>();
    private final List<Action> actions = new ArrayList<>();
    private int ticks;
    @Nullable
    private String winner;

    /**
     * a player of the game
     *
     * @param team      the name of his team, null: none
     * @param color     the color of the team (name of a NamedTextColor)
     * @param skin      the textures of his profile, null: unknown (the skin of his name is looked up)
     * @param signature the signature of the textures
     */
    public record Actor(UUID uuid, String name, @Nullable String team, @Nullable String color, @Nullable String skin,
                        @Nullable String signature) {
    }

    /**
     * the header of a recording
     *
     * @param map      the folder in maps/ the game was played in, null: a generated world
     * @param seed     the seed of the world
     * @param start    when the recording began (milliseconds since 1970)
     * @param ticks    its length
     * @param day      Varo: the day of the play time that was recorded (e.g. 2026-10-10)
     * @param startDay Varo: the day the game began
     * @param x        the center of the arena - where the viewers start, positions are saved relative to it
     * @param winner   the name of the team that won, null: nobody (yet)
     */
    public record Info(String id, String arena, GameType type, @Nullable String map, long seed, long start, int ticks,
                       @Nullable String day, @Nullable String startDay, double x, double y, double z,
                       @Nullable String winner, List<Actor> actors) {

        /**
         * @return the day of the Varo that was recorded (1: the day it began), 0 for other games
         */
        public int dayNumber() {
            if (day == null || startDay == null) return 0;
            try {
                return (int) ChronoUnit.DAYS.between(LocalDate.parse(startDay), LocalDate.parse(day)) + 1;
            } catch (DateTimeParseException ex) {
                return 0;
            }
        }

        public int seconds() {
            return ticks / 20;
        }

        private Info with(int ticks, @Nullable String winner, List<Actor> actors) {
            return new Info(id, arena, type, map, seed, start, ticks, day, startDay, x, y, z, winner, List.copyOf(actors));
        }
    }

    /**
     * a new recording (the actors and the length of the info are ignored)
     */
    public Recording(Info info) {
        this.info = info;
        this.actors.addAll(info.actors());
        this.ticks = info.ticks();
        this.winner = info.winner();
    }

    /**
     * @return the header as it is now
     */
    public Info getInfo() {
        return info.with(ticks, winner, actors);
    }

    public String getId() {
        return info.id();
    }

    public List<Actor> getActors() {
        return actors;
    }

    public List<Action> getActions() {
        return actions;
    }

    public int getTicks() {
        return ticks;
    }

    void setTicks(int ticks) {
        this.ticks = ticks;
    }

    void setWinner(@Nullable String winner) {
        this.winner = winner;
    }

    /**
     * @return the index of the player, added (or updated: new name, team or skin) if needed
     */
    int actor(Actor actor) {
        for (int i = 0; i < actors.size(); i++) {
            if (!actors.get(i).uuid().equals(actor.uuid())) continue;
            actors.set(i, actor);
            return i;
        }
        actors.add(actor);
        return actors.size() - 1;
    }

    /**
     * @return a copy that can be written while the recording goes on
     */
    Recording copy() {
        Recording copy = new Recording(getInfo());
        copy.actions.addAll(actions);
        return copy;
    }

    // ---------------------------------------------------------------- file

    public void write(File file) throws IOException {
        File folder = file.getParentFile();
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Couldn't create " + folder);
        File temp = new File(folder, file.getName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(Files.newOutputStream(temp.toPath()))))) {
            writeInfo(out, getInfo());
            Writer writer = new Writer(out, info);
            out.writeInt(actions.size());
            int last = 0;
            for (Action action : actions) {
                writeVarInt(out, action.tick() - last);
                last = action.tick();
                writer.write(action);
            }
        }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    public static Recording read(File file) throws IOException {
        try (DataInputStream in = open(file)) {
            Recording recording = new Recording(readInfo(in));
            Reader reader = new Reader(in, recording.info);
            int count = in.readInt();
            int tick = 0;
            for (int i = 0; i < count; i++) {
                tick += readVarInt(in);
                recording.actions.add(reader.read(tick));
            }
            return recording;
        }
    }

    /**
     * @return only the header of the recording
     */
    public static Info readInfo(File file) throws IOException {
        try (DataInputStream in = open(file)) {
            return readInfo(in);
        }
    }

    private static DataInputStream open(File file) throws IOException {
        return new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file.toPath()))));
    }

    private static void writeInfo(DataOutputStream out, Info info) throws IOException {
        out.writeInt(MAGIC);
        out.writeByte(VERSION);
        out.writeUTF(info.id());
        out.writeUTF(info.arena());
        out.writeUTF(info.type().getKey());
        writeNullable(out, info.map());
        out.writeLong(info.seed());
        out.writeLong(info.start());
        out.writeInt(info.ticks());
        writeNullable(out, info.day());
        writeNullable(out, info.startDay());
        out.writeDouble(info.x());
        out.writeDouble(info.y());
        out.writeDouble(info.z());
        writeNullable(out, info.winner());
        out.writeShort(info.actors().size());
        for (Actor actor : info.actors()) {
            out.writeLong(actor.uuid().getMostSignificantBits());
            out.writeLong(actor.uuid().getLeastSignificantBits());
            out.writeUTF(actor.name());
            writeNullable(out, actor.team());
            writeNullable(out, actor.color());
            writeNullable(out, actor.skin());
            writeNullable(out, actor.signature());
        }
    }

    private static Info readInfo(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) throw new IOException("not a recording of SurvivalGames");
        int version = in.readUnsignedByte();
        if (version != VERSION) throw new IOException("unknown version " + version + " (a newer SurvivalGames?)");
        String id = in.readUTF();
        String arena = in.readUTF();
        String typeKey = in.readUTF();
        GameType type = GameType.parse(typeKey);
        if (type == null) throw new IOException("unknown game type " + typeKey);
        String map = readNullable(in);
        long seed = in.readLong();
        long start = in.readLong();
        int ticks = in.readInt();
        String day = readNullable(in);
        String startDay = readNullable(in);
        double x = in.readDouble();
        double y = in.readDouble();
        double z = in.readDouble();
        String winner = readNullable(in);
        List<Actor> actors = new ArrayList<>();
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            actors.add(new Actor(new UUID(in.readLong(), in.readLong()), in.readUTF(), readNullable(in),
                    readNullable(in), readNullable(in), readNullable(in)));
        }
        return new Info(id, arena, type, map, seed, start, ticks, day, startDay, x, y, z, winner, actors);
    }

    private static void writeNullable(DataOutputStream out, @Nullable String text) throws IOException {
        out.writeBoolean(text != null);
        if (text != null) out.writeUTF(text);
    }

    @Nullable
    private static String readNullable(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte(value & 0x7F | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int part = in.readUnsignedByte();
            value |= (part & 0x7F) << shift;
            if ((part & 0x80) == 0) return value;
        }
        throw new IOException("invalid number");
    }

    /**
     * 256 steps per turn (like the Minecraft protocol)
     */
    private static int angle(float degrees) {
        return Math.floorMod(Math.round(degrees * 256f / 360f), 256);
    }

    /**
     * Positions are written as floats relative to the center of the arena, texts that repeat (block data, poses)
     * once.
     */
    private static final class Writer {
        private final DataOutputStream out;
        private final Info info;
        private final Map<String, Integer> texts = new HashMap<>();

        Writer(DataOutputStream out, Info info) {
            this.out = out;
            this.info = info;
        }

        void write(Action action) throws IOException {
            switch (action) {
                case Action.Spawn a -> {
                    type(0, a.actor());
                    location(a.x(), a.y(), a.z(), a.yaw(), a.pitch());
                }
                case Action.Move a -> {
                    type(1, a.actor());
                    location(a.x(), a.y(), a.z(), a.yaw(), a.pitch());
                }
                case Action.Despawn a -> {
                    type(2, a.actor());
                    out.writeBoolean(a.dead());
                }
                case Action.Pose a -> {
                    type(3, a.actor());
                    text(a.pose());
                    out.writeByte((a.burning() ? 1 : 0) | (a.invisible() ? 2 : 0));
                }
                case Action.Equip a -> {
                    type(4, a.actor());
                    out.writeByte(a.slot());
                    bytes(a.item());
                }
                case Action.Swing a -> {
                    type(5, a.actor());
                    out.writeBoolean(a.offHand());
                }
                case Action.Hurt a -> type(6, a.actor());
                case Action.Health a -> {
                    type(7, a.actor());
                    out.writeFloat(a.health());
                }
                case Action.SetBlock a -> {
                    out.writeByte(8);
                    out.writeInt(a.x());
                    out.writeShort(a.y());
                    out.writeInt(a.z());
                    text(a.before());
                    text(a.after());
                }
                case Action.Message a -> {
                    out.writeByte(9);
                    bytes(a.json().getBytes(StandardCharsets.UTF_8));
                }
                case Action.PropSpawn a -> {
                    out.writeByte(10);
                    writeVarInt(out, a.prop());
                    bytes(a.item());
                    position(a.x(), a.y(), a.z());
                }
                case Action.PropMove a -> {
                    out.writeByte(11);
                    writeVarInt(out, a.prop());
                    position(a.x(), a.y(), a.z());
                }
                case Action.PropRemove a -> {
                    out.writeByte(12);
                    writeVarInt(out, a.prop());
                }
                case Action.Border a -> {
                    out.writeByte(13);
                    out.writeDouble(a.x());
                    out.writeDouble(a.z());
                    out.writeDouble(a.size());
                }
                case Action.Sky a -> {
                    out.writeByte(14);
                    out.writeLong(a.time());
                    out.writeBoolean(a.storm());
                }
                case Action.Clock a -> {
                    out.writeByte(15);
                    out.writeLong(a.millis());
                }
                case Action.Lightning a -> {
                    out.writeByte(16);
                    position(a.x(), a.y(), a.z());
                }
            }
        }

        private void type(int type, int actor) throws IOException {
            out.writeByte(type);
            out.writeShort(actor);
        }

        private void position(double x, double y, double z) throws IOException {
            out.writeFloat((float) (x - info.x()));
            out.writeFloat((float) (y - info.y()));
            out.writeFloat((float) (z - info.z()));
        }

        private void location(double x, double y, double z, float yaw, float pitch) throws IOException {
            position(x, y, z);
            out.writeByte(angle(yaw));
            out.writeByte(angle(pitch));
        }

        private void bytes(byte @Nullable [] bytes) throws IOException {
            writeVarInt(out, bytes == null ? 0 : bytes.length);
            if (bytes != null) out.write(bytes);
        }

        private void text(String text) throws IOException {
            Integer index = texts.get(text);
            if (index != null) {
                writeVarInt(out, index);
                return;
            }
            writeVarInt(out, texts.size());
            texts.put(text, texts.size());
            out.writeUTF(text);
        }
    }

    private static final class Reader {
        private final DataInputStream in;
        private final Info info;
        private final List<String> texts = new ArrayList<>();

        Reader(DataInputStream in, Info info) {
            this.in = in;
            this.info = info;
        }

        Action read(int tick) throws IOException {
            int type = in.readUnsignedByte();
            return switch (type) {
                case 0 -> {
                    int actor = in.readUnsignedShort();
                    yield new Action.Spawn(tick, actor, x(), y(), z(), degrees(), pitch());
                }
                case 1 -> {
                    int actor = in.readUnsignedShort();
                    yield new Action.Move(tick, actor, x(), y(), z(), degrees(), pitch());
                }
                case 2 -> new Action.Despawn(tick, in.readUnsignedShort(), in.readBoolean());
                case 3 -> {
                    int actor = in.readUnsignedShort();
                    String pose = text();
                    int flags = in.readUnsignedByte();
                    yield new Action.Pose(tick, actor, pose, (flags & 1) != 0, (flags & 2) != 0);
                }
                case 4 -> new Action.Equip(tick, in.readUnsignedShort(), in.readUnsignedByte(), bytes());
                case 5 -> new Action.Swing(tick, in.readUnsignedShort(), in.readBoolean());
                case 6 -> new Action.Hurt(tick, in.readUnsignedShort());
                case 7 -> new Action.Health(tick, in.readUnsignedShort(), in.readFloat());
                case 8 -> {
                    int x = in.readInt();
                    int y = in.readShort();
                    int z = in.readInt();
                    yield new Action.SetBlock(tick, x, y, z, text(), text());
                }
                case 9 -> {
                    byte[] json = bytes();
                    yield new Action.Message(tick, json == null ? "" : new String(json, StandardCharsets.UTF_8));
                }
                case 10 -> {
                    int prop = readVarInt(in);
                    byte[] item = bytes();
                    yield new Action.PropSpawn(tick, prop, item == null ? new byte[0] : item, x(), y(), z());
                }
                case 11 -> new Action.PropMove(tick, readVarInt(in), x(), y(), z());
                case 12 -> new Action.PropRemove(tick, readVarInt(in));
                case 13 -> new Action.Border(tick, in.readDouble(), in.readDouble(), in.readDouble());
                case 14 -> new Action.Sky(tick, in.readLong(), in.readBoolean());
                case 15 -> new Action.Clock(tick, in.readLong());
                case 16 -> new Action.Lightning(tick, x(), y(), z());
                default -> throw new IOException("unknown action " + type);
            };
        }

        private double x() throws IOException {
            return info.x() + in.readFloat();
        }

        private double y() throws IOException {
            return info.y() + in.readFloat();
        }

        private double z() throws IOException {
            return info.z() + in.readFloat();
        }

        private float degrees() throws IOException {
            return in.readUnsignedByte() * 360f / 256f;
        }

        /**
         * -90 (up) to 90 (down)
         */
        private float pitch() throws IOException {
            float degrees = degrees();
            return degrees > 180 ? degrees - 360 : degrees;
        }

        private byte @Nullable [] bytes() throws IOException {
            int length = readVarInt(in);
            if (length == 0) return null;
            byte[] bytes = new byte[length];
            in.readFully(bytes);
            return bytes;
        }

        private String text() throws IOException {
            int index = readVarInt(in);
            if (index < texts.size()) return texts.get(index);
            if (index > texts.size()) throw new IOException("invalid text " + index);
            String text = in.readUTF();
            texts.add(text);
            return text;
        }
    }

}
