package net.prismarin.api.server;

import java.time.Instant;
import java.util.Objects;

public record ServerStatus(String name, boolean online, int players, int maxPlayers, long latencyMillis,
                           String version, boolean prismarin, double tps, double mspt, long usedMemoryMb,
                           long maxMemoryMb, long uptimeSeconds, Instant updatedAt) {

    public ServerStatus {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static ServerStatus offline(String name, Instant updatedAt) {
        return new ServerStatus(name, false, 0, 0, -1L, "", false, 0D, 0D, 0L, 0L, 0L, updatedAt);
    }
}
