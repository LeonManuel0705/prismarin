package net.prismarin.core.network;

public final class UptimeFormat {

    private UptimeFormat() {
        throw new AssertionError();
    }

    public static String compact(long seconds) {
        long days = seconds / 86_400L;
        long hours = seconds % 86_400L / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
}
