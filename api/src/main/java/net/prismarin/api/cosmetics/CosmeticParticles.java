package net.prismarin.api.cosmetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public record CosmeticParticles(String shape, String particle, List<Integer> colors, float size, int count,
                                double spread, double speed, double radius, double height, int points,
                                double spin, String sound, String kind, int duration, String variant)
        implements CosmeticLook {

    public static final Pattern KEY = Pattern.compile("[a-z0-9_.-]{1,64}");
    public static final String PARTICLES = "particles";
    public static final int NO_COLOR = -1;
    public static final int MAX_COUNT = 50;
    public static final int MAX_POINTS = 200;
    public static final int MAX_DURATION = 200;

    public CosmeticParticles(String shape, String particle, List<Integer> colors, float size, int count,
                             double spread, double speed, double radius, double height, int points, double spin,
                             String sound) {
        this(shape, particle, colors, size, count, spread, speed, radius, height, points, spin, sound, PARTICLES, 0,
                "");
    }

    public CosmeticParticles {
        shape = key(shape, "shape");
        particle = key(particle, "particle");
        List<Integer> palette = new ArrayList<>();
        if (colors != null) {
            for (Integer color : colors) {
                if (color != null) {
                    palette.add(color & 0xFFFFFF);
                }
            }
        }
        colors = List.copyOf(palette);
        size = size <= 0f || Float.isNaN(size) ? 1f : Math.min(size, 4f);
        count = Math.clamp(count, 1, MAX_COUNT);
        spread = clamp(spread, 0.0, 4.0);
        speed = clamp(speed, 0.0, 4.0);
        radius = clamp(radius, 0.0, 6.0);
        height = clamp(height, -3.0, 5.0);
        points = Math.clamp(points, 0, MAX_POINTS);
        spin = clamp(spin, -2.0, 2.0);
        sound = sound == null ? "" : sound.strip().toLowerCase(Locale.ROOT);
        kind = kind == null || kind.isBlank() ? PARTICLES : key(kind, "effect kind");
        duration = Math.clamp(duration, 0, MAX_DURATION);
        variant = variant == null || variant.isBlank() ? "" : key(variant, "variant");
    }

    public static CosmeticParticles of(String shape, String particle) {
        return new CosmeticParticles(shape, particle, List.of(), 1f, 1, 0.0, 0.0, 0.0, 0.0, 0, 0.0, "", PARTICLES, 0,
                "");
    }

    public boolean runs() {
        return duration > 0;
    }

    public int color(int index) {
        return colors.isEmpty() ? NO_COLOR : colors.get(Math.floorMod(index, colors.size()));
    }

    public CosmeticParticles withShape(String value) {
        return new CosmeticParticles(value, particle, colors, size, count, spread, speed, radius, height, points,
                spin, sound, kind, duration, variant);
    }

    public CosmeticParticles withColors(Integer... values) {
        return new CosmeticParticles(shape, particle, List.of(values), size, count, spread, speed, radius, height,
                points, spin, sound, kind, duration, variant);
    }

    public CosmeticParticles withSize(float value) {
        return new CosmeticParticles(shape, particle, colors, value, count, spread, speed, radius, height, points,
                spin, sound, kind, duration, variant);
    }

    public CosmeticParticles withCount(int value) {
        return new CosmeticParticles(shape, particle, colors, size, value, spread, speed, radius, height, points,
                spin, sound, kind, duration, variant);
    }

    public CosmeticParticles withSpread(double value) {
        return new CosmeticParticles(shape, particle, colors, size, count, value, speed, radius, height, points,
                spin, sound, kind, duration, variant);
    }

    public CosmeticParticles withSpeed(double value) {
        return new CosmeticParticles(shape, particle, colors, size, count, spread, value, radius, height, points,
                spin, sound, kind, duration, variant);
    }

    public CosmeticParticles withRadius(double value) {
        return new CosmeticParticles(shape, particle, colors, size, count, spread, speed, value, height, points,
                spin, sound, kind, duration, variant);
    }

    public CosmeticParticles withHeight(double value) {
        return new CosmeticParticles(shape, particle, colors, size, count, spread, speed, radius, value, points,
                spin, sound, kind, duration, variant);
    }

    public CosmeticParticles withPoints(int value) {
        return new CosmeticParticles(shape, particle, colors, size, count, spread, speed, radius, height, value,
                spin, sound, kind, duration, variant);
    }

    public CosmeticParticles withSpin(double value) {
        return new CosmeticParticles(shape, particle, colors, size, count, spread, speed, radius, height, points,
                value, sound, kind, duration, variant);
    }

    public CosmeticParticles withSound(String value) {
        return new CosmeticParticles(shape, particle, colors, size, count, spread, speed, radius, height, points,
                spin, value, kind, duration, variant);
    }

    public CosmeticParticles withKind(String value) {
        return new CosmeticParticles(shape, particle, colors, size, count, spread, speed, radius, height, points,
                spin, sound, value, duration, variant);
    }

    public CosmeticParticles withDuration(int value) {
        return new CosmeticParticles(shape, particle, colors, size, count, spread, speed, radius, height, points,
                spin, sound, kind, value, variant);
    }

    public CosmeticParticles withVariant(String value) {
        return new CosmeticParticles(shape, particle, colors, size, count, spread, speed, radius, height, points,
                spin, sound, kind, duration, value);
    }

    private static String key(String value, String what) {
        String key = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("'" + value + "' is no " + what + " name");
        }
        return key;
    }

    private static double clamp(double value, double min, double max) {
        return Double.isNaN(value) ? min : Math.clamp(value, min, max);
    }
}
