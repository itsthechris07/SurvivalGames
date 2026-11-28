package com.christian34.survivalgames.arena;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * A region file of a world (Anvil, r.&lt;x&gt;.&lt;z&gt;.mca): 32x32 chunks, a header with the position of every chunk
 * (in sectors of 4 KiB) and their timestamps, then the compressed chunks. Only what trimming a map needs: reading the
 * chunks as NBT and writing the file again without some of them.
 *
 * @author Christian34
 */
final class RegionFile {
    static final int SECTOR = 4096;
    private static final int CHUNKS = 1024;
    private static final int COMPRESSION_GZIP = 1;
    private static final int COMPRESSION_ZLIB = 2;
    private static final int COMPRESSION_NONE = 3;
    /**
     * the chunk is too big for the region file, it is stored in c.&lt;x&gt;.&lt;z&gt;.mcc next to it
     */
    static final int EXTERNAL = 128;

    /**
     * a chunk as it is stored: the compression type and the compressed data
     */
    record Chunk(byte[] payload, int timestamp) {
        int compression() {
            return payload.length == 0 ? 0 : payload[0] & 0xFF;
        }

        /**
         * @return the size in the region file (whole sectors)
         */
        long size() {
            return (4L + payload.length + SECTOR - 1) / SECTOR * SECTOR;
        }
    }

    private RegionFile() {
    }

    /**
     * @return the chunks by their index (x + z * 32 inside the region), a damaged entry is skipped
     */
    static Map<Integer, Chunk> read(File file) throws IOException {
        Map<Integer, Chunk> chunks = new HashMap<>();
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            long length = in.length();
            if (length < 2L * SECTOR) return chunks;
            byte[] header = new byte[2 * SECTOR];
            in.readFully(header);
            ByteBuffer buffer = ByteBuffer.wrap(header);
            for (int i = 0; i < CHUNKS; i++) {
                int location = buffer.getInt(i * 4);
                int timestamp = buffer.getInt(SECTOR + i * 4);
                long offset = (long) (location >>> 8) * SECTOR;
                if (location == 0 || offset < 2L * SECTOR || offset + 5 > length) continue;
                in.seek(offset);
                int size = in.readInt();
                if (size <= 0 || offset + 4 + size > length) continue;
                byte[] payload = new byte[size];
                in.readFully(payload);
                chunks.put(i, new Chunk(payload, timestamp));
            }
        }
        return chunks;
    }

    /**
     * writes the region file with these chunks only (an empty region is deleted)
     */
    static void write(File file, Map<Integer, Chunk> chunks) throws IOException {
        if (chunks.isEmpty()) {
            Files.deleteIfExists(file.toPath());
            return;
        }
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        ByteBuffer header = ByteBuffer.allocate(2 * SECTOR);
        List<byte[]> data = new ArrayList<>();
        int sector = 2;
        for (int i = 0; i < CHUNKS; i++) {
            Chunk chunk = chunks.get(i);
            if (chunk == null) continue;
            int sectors = (int) (chunk.size() / SECTOR);
            ByteBuffer bytes = ByteBuffer.allocate(sectors * SECTOR);
            bytes.putInt(chunk.payload().length).put(chunk.payload());
            data.add(bytes.array());
            header.putInt(i * 4, sector << 8 | Math.min(sectors, 255));
            header.putInt(SECTOR + i * 4, chunk.timestamp());
            sector += sectors;
        }
        try (OutputStream out = Files.newOutputStream(temp.toPath())) {
            out.write(header.array());
            for (byte[] bytes : data) {
                out.write(bytes);
            }
        }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * @return the chunk as NBT (compounds are maps, lists are lists, int arrays are int[], other arrays are skipped),
     * null if it is stored in a way this can't read (an external or LZ4 compressed chunk)
     */
    @Nullable
    static Map<String, Object> readNbt(Chunk chunk) throws IOException {
        byte[] payload = chunk.payload();
        if (payload.length < 2) return null;
        InputStream raw = new ByteArrayInputStream(payload, 1, payload.length - 1);
        InputStream in = switch (chunk.compression()) {
            case COMPRESSION_GZIP -> new GZIPInputStream(raw);
            case COMPRESSION_ZLIB -> new InflaterInputStream(raw);
            case COMPRESSION_NONE -> raw;
            default -> null;
        };
        if (in == null) return null;
        return readRoot(in);
    }

    /**
     * @return the NBT of a gzip compressed file (level.dat), like {@link #readNbt(Chunk)}
     */
    @Nullable
    static Map<String, Object> readNbt(File file) throws IOException {
        return readRoot(new GZIPInputStream(Files.newInputStream(file.toPath())));
    }

    @Nullable
    private static Map<String, Object> readRoot(InputStream in) throws IOException {
        try (DataInputStream data = new DataInputStream(in)) {
            if (data.readByte() != 10) return null;
            data.readUTF();
            return readCompound(data);
        }
    }

    private static Map<String, Object> readCompound(DataInputStream in) throws IOException {
        Map<String, Object> compound = new HashMap<>();
        while (true) {
            byte type = in.readByte();
            if (type == 0) return compound;
            String name = in.readUTF();
            Object value = readTag(in, type);
            if (value != null) compound.put(name, value);
        }
    }

    @Nullable
    private static Object readTag(DataInputStream in, byte type) throws IOException {
        return switch (type) {
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> skip(in, 1);
            case 8 -> in.readUTF();
            case 9 -> {
                byte elementType = in.readByte();
                int length = in.readInt();
                List<Object> list = new ArrayList<>(Math.max(0, Math.min(length, 4096)));
                for (int i = 0; i < length; i++) {
                    list.add(readTag(in, elementType));
                }
                yield list;
            }
            case 10 -> readCompound(in);
            case 11 -> {
                int[] array = new int[in.readInt()];
                for (int i = 0; i < array.length; i++) {
                    array[i] = in.readInt();
                }
                yield array;
            }
            case 12 -> skip(in, 8);
            default -> throw new IOException("Unknown NBT tag " + type);
        };
    }

    /**
     * skips an array (the block data isn't needed)
     */
    @Nullable
    private static Object skip(DataInputStream in, int size) throws IOException {
        int length = in.readInt();
        in.skipNBytes((long) length * size);
        return null;
    }

}
