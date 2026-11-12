package net.prismarin.shared.time;

public final class Streaks {

    public static final long NEVER = Long.MIN_VALUE;

    public record Step(int streak, boolean counted, int lost, int missed) {

        public boolean broken() {
            return lost > 0;
        }
    }

    private Streaks() {
        throw new AssertionError();
    }

    public static Step next(long lastDay, int streak, long today, int graceDays) {
        if (lastDay == NEVER || streak <= 0) {
            return new Step(1, true, 0, 0);
        }
        if (today <= lastDay) {
            return new Step(streak, false, 0, 0);
        }
        long gap = today - lastDay;
        int missed = (int) Math.min(Integer.MAX_VALUE, gap - 1L);
        if (gap <= 1L + Math.max(0, graceDays)) {
            return new Step(streak == Integer.MAX_VALUE ? streak : streak + 1, true, 0, missed);
        }
        return new Step(1, true, streak, missed);
    }

    public static int current(long lastDay, int streak, long today, int graceDays) {
        if (lastDay == NEVER || streak <= 0) {
            return 0;
        }
        if (today <= lastDay) {
            return streak;
        }
        return today - lastDay <= 1L + Math.max(0, graceDays) ? streak : 0;
    }
}
