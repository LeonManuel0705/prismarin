package net.prismarin.api.level;

public record LevelProgress(int level, long xp, long required) {

    public static final int MAX_LEVEL = 1_000_000;

    public static long requirement(int level) {
        return 500L + (long) level * 250L;
    }

    public static LevelProgress of(int level, long xp) {
        return new LevelProgress(level, xp, requirement(level));
    }

    public long remaining() {
        return Math.max(0L, required - xp);
    }

    public double fraction() {
        if (required <= 0L) {
            return 1D;
        }
        return Math.clamp((double) xp / required, 0D, 1D);
    }
}
