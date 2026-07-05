package net.prismarin.lobby.config;

import java.util.Objects;

public record SpawnPoint(String world, double x, double y, double z, float yaw, float pitch) {

    public SpawnPoint {
        Objects.requireNonNull(world, "world");
    }
}
