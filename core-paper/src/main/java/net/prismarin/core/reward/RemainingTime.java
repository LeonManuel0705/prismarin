package net.prismarin.core.reward;

public final class RemainingTime {

    public record Parts(long hours, long minutes) {
    }

    private RemainingTime() {
        throw new AssertionError();
    }

    public static Parts of(long millis) {
        long minutes = Math.max(1L, (Math.max(0L, millis) + 59_999L) / 60_000L);
        return new Parts(minutes / 60L, minutes % 60L);
    }
}
