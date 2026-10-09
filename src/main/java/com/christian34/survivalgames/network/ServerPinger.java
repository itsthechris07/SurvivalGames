package com.christian34.survivalgames.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Reads the MOTD of a Minecraft server like the server list of the client does (Server List Ping): handshake with
 * the next state "status", status request, JSON response.
 *
 * @author Christian34
 */
public final class ServerPinger {
    private static final int MAX_RESPONSE_LENGTH = 1 << 16;

    private ServerPinger() {
    }

    /**
     * @return the MOTD as plain text, null if the server isn't reachable
     */
    @Nullable
    public static String ping(String host, int port, int timeoutMillis) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            OutputStream out = socket.getOutputStream();

            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            writeVarInt(handshake, 0x00);
            // protocol version: -1 = only asking for the status
            writeVarInt(handshake, -1);
            writeString(handshake, host);
            new DataOutputStream(handshake).writeShort(port);
            writeVarInt(handshake, 1);
            writePacket(out, handshake.toByteArray());
            // status request
            writePacket(out, new byte[]{0x00});
            out.flush();

            DataInputStream in = new DataInputStream(socket.getInputStream());
            int length = readVarInt(in);
            if (length <= 0 || length > MAX_RESPONSE_LENGTH || readVarInt(in) != 0x00) return null;
            int jsonLength = readVarInt(in);
            if (jsonLength <= 0 || jsonLength > MAX_RESPONSE_LENGTH) return null;
            byte[] json = new byte[jsonLength];
            in.readFully(json);
            return motd(new String(json, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    /**
     * @return the description of the status response as plain text (a string or a text component)
     */
    @Nullable
    static String motd(String json) {
        JsonObject response = JsonParser.parseString(json).getAsJsonObject();
        JsonElement description = response.get("description");
        if (description == null || description.isJsonNull()) return null;
        if (description.isJsonPrimitive()) return description.getAsString();
        return PlainTextComponentSerializer.plainText().serialize(GsonComponentSerializer.gson().deserializeFromTree(description));
    }

    private static void writePacket(OutputStream out, byte[] packet) throws IOException {
        ByteArrayOutputStream length = new ByteArrayOutputStream();
        writeVarInt(length, packet.length);
        out.write(length.toByteArray());
        out.write(packet);
    }

    private static void writeString(ByteArrayOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    static void writeVarInt(OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        for (int position = 0; position < 35; position += 7) {
            int read = in.read();
            if (read < 0) throw new IOException("end of stream");
            value |= (read & 0x7F) << position;
            if ((read & 0x80) == 0) return value;
        }
        throw new IOException("VarInt too long");
    }

}
