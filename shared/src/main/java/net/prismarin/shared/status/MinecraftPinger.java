package net.prismarin.shared.status;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class MinecraftPinger {

    private static final int PROTOCOL_ANY = -1;
    private static final int MAX_RESPONSE_BYTES = 1 << 20;

    private MinecraftPinger() {
        throw new AssertionError();
    }

    public record Result(int players, int maxPlayers, String version, long latencyMillis) {
    }

    public static Result ping(String host, int port, Duration timeout) throws IOException {
        try (Socket socket = new Socket()) {
            int millis = (int) timeout.toMillis();
            socket.connect(new InetSocketAddress(host, port), millis);
            socket.setSoTimeout(millis);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            writeVarInt(handshake, 0x00);
            writeVarInt(handshake, PROTOCOL_ANY);
            writeString(handshake, host);
            handshake.write((port >> 8) & 0xFF);
            handshake.write(port & 0xFF);
            writeVarInt(handshake, 1);
            writePacket(out, handshake.toByteArray());
            writePacket(out, new byte[]{0x00});

            readVarInt(in);
            if (readVarInt(in) != 0x00) {
                throw new IOException("unexpected status packet");
            }
            int length = readVarInt(in);
            if (length < 0 || length > MAX_RESPONSE_BYTES) {
                throw new IOException("invalid status length " + length);
            }
            byte[] json = new byte[length];
            in.readFully(json);

            long sent = System.nanoTime();
            ByteArrayOutputStream ping = new ByteArrayOutputStream();
            writeVarInt(ping, 0x01);
            new DataOutputStream(ping).writeLong(sent);
            writePacket(out, ping.toByteArray());
            readVarInt(in);
            readVarInt(in);
            in.readLong();
            long latency = Duration.ofNanos(System.nanoTime() - sent).toMillis();

            return parse(new String(json, StandardCharsets.UTF_8), latency);
        }
    }

    static Result parse(String json, long latency) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject players = root.has("players") ? root.getAsJsonObject("players") : new JsonObject();
        JsonObject version = root.has("version") ? root.getAsJsonObject("version") : new JsonObject();
        return new Result(
                players.has("online") ? players.get("online").getAsInt() : 0,
                players.has("max") ? players.get("max").getAsInt() : 0,
                version.has("name") ? version.get("name").getAsString() : "",
                latency);
    }

    private static void writePacket(OutputStream out, byte[] body) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream(body.length + 5);
        writeVarInt(frame, body.length);
        frame.write(body);
        out.write(frame.toByteArray());
        out.flush();
    }

    static void writeVarInt(OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    static int readVarInt(InputStream in) throws IOException {
        int result = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int read = in.read();
            if (read < 0) {
                throw new IOException("connection closed");
            }
            result |= (read & 0x7F) << shift;
            if ((read & 0x80) == 0) {
                return result;
            }
        }
        throw new IOException("varint too long");
    }

    private static void writeString(OutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }
}
