package net.prismarin.core.essentials;

import java.util.Locale;
import java.util.Optional;

public final class SpeedLevels {

    public static final float DEFAULT_WALK = 0.2F;
    public static final float DEFAULT_FLY = 0.1F;
    public static final int MIN = 1;
    public static final int MAX = 10;
    public static final String RESET = "reset";

    private SpeedLevels() {
        throw new AssertionError();
    }

    public static Optional<Integer> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String word = text.trim().toLowerCase(Locale.ROOT);
        if (word.equals(RESET) || word.equals("default")) {
            return Optional.of(MIN);
        }
        try {
            int level = Integer.parseInt(word);
            return level >= MIN && level <= MAX ? Optional.of(level) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static float value(int level, boolean flying) {
        float base = flying ? DEFAULT_FLY : DEFAULT_WALK;
        int clamped = Math.max(MIN, Math.min(MAX, level));
        return base + (clamped - MIN) / (float) (MAX - MIN) * (1.0F - base);
    }
}
