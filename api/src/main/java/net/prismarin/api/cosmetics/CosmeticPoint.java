package net.prismarin.api.cosmetics;

public record CosmeticPoint(double x, double y, double z, int color) {

    public CosmeticPoint {
        color = color < 0 ? CosmeticParticles.NO_COLOR : color & 0xFFFFFF;
    }

    public static CosmeticPoint of(double x, double y, double z) {
        return new CosmeticPoint(x, y, z, CosmeticParticles.NO_COLOR);
    }

    public CosmeticPoint withColor(int value) {
        return new CosmeticPoint(x, y, z, value);
    }
}
