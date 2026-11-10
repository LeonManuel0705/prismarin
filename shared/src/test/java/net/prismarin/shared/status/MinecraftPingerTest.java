package net.prismarin.shared.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MinecraftPingerTest {

    private static final String STATUS = "{\"version\":{\"name\":\"Paper 1.21.11\",\"protocol\":774},"
            + "\"players\":{\"max\":100,\"online\":7},\"description\":{\"text\":\"hi \",\"extra\":[{\"text\":\"there\"}]}}";

    @Test
    void readsPlayersVersionAndLatencyFromStatusPing() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Thread responder = Thread.ofVirtual().start(() -> respond(server));
            MinecraftPinger.Result result = MinecraftPinger.ping("127.0.0.1", server.getLocalPort(),
                    Duration.ofSeconds(2));
            responder.join(2000);
            assertEquals(7, result.players());
            assertEquals(100, result.maxPlayers());
            assertEquals("Paper 1.21.11", result.version());
            assertTrue(result.latencyMillis() >= 0);
            assertEquals("hi there", result.description());
        }
    }

    @Test
    void closedPortFails() throws IOException {
        int port;
        try (ServerSocket server = new ServerSocket(0)) {
            port = server.getLocalPort();
        }
        assertThrows(IOException.class, () -> MinecraftPinger.ping("127.0.0.1", port, Duration.ofMillis(500)));
    }

    @Test
    void missingFieldsDefaultToZero() {
        MinecraftPinger.Result result = MinecraftPinger.parse("{}", 3L);
        assertEquals(0, result.players());
        assertEquals("", result.version());
    }

    private static void respond(ServerSocket server) {
        try (Socket client = server.accept()) {
            DataInputStream in = new DataInputStream(client.getInputStream());
            OutputStream out = client.getOutputStream();
            skipPacket(in);
            skipPacket(in);
            ByteArrayOutputStream status = new ByteArrayOutputStream();
            MinecraftPinger.writeVarInt(status, 0x00);
            byte[] json = STATUS.getBytes(StandardCharsets.UTF_8);
            MinecraftPinger.writeVarInt(status, json.length);
            status.write(json);
            frame(out, status.toByteArray());
            int length = MinecraftPinger.readVarInt(in);
            byte[] ping = new byte[length];
            in.readFully(ping);
            frame(out, ping);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void skipPacket(DataInputStream in) throws IOException {
        int length = MinecraftPinger.readVarInt(in);
        in.readFully(new byte[length]);
    }

    private static void frame(OutputStream out, byte[] body) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        MinecraftPinger.writeVarInt(frame, body.length);
        frame.write(body);
        out.write(frame.toByteArray());
        out.flush();
    }
}
