package net.prismarin.api.cosmetics;

import java.util.Objects;

public record CosmeticSpot(String world, double x, double y, double z, double yaw) {

    public CosmeticSpot {
        Objects.requireNonNull(world, "world");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("a spot needs finite coordinates");
        }
        yaw = Double.isFinite(yaw) ? yaw : 0.0;
    }

    public static CosmeticSpot of(String world, double x, double y, double z) {
        return new CosmeticSpot(world, x, y, z, 0.0);
    }

    public static CosmeticSpot of(String world, double x, double y, double z, double yaw) {
        return new CosmeticSpot(world, x, y, z, yaw);
    }
}
