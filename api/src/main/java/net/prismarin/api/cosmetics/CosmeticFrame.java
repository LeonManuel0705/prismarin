package net.prismarin.api.cosmetics;

import java.util.Objects;
import java.util.UUID;

public record CosmeticFrame(UUID player, long tick, double yaw, boolean moving, CosmeticParticles look) {

    public CosmeticFrame {
        Objects.requireNonNull(look, "look");
    }

    public double forwardX() {
        return -Math.sin(Math.toRadians(yaw));
    }

    public double forwardZ() {
        return Math.cos(Math.toRadians(yaw));
    }

    public double rightX() {
        return -Math.cos(Math.toRadians(yaw));
    }

    public double rightZ() {
        return -Math.sin(Math.toRadians(yaw));
    }
}
