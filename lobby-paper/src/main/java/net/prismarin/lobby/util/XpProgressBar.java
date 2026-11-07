package net.prismarin.lobby.util;

public final class XpProgressBar {

    private static final String FILLED = "▮";
    private static final String EMPTY = "▯";

    private XpProgressBar() {
        throw new AssertionError();
    }

    public static int filledSegments(long xp, long required, int segments) {
        if (segments < 1) {
            throw new IllegalArgumentException("segments must be at least 1");
        }
        if (required <= 0 || xp >= required) {
            return segments;
        }
        if (xp <= 0) {
            return 0;
        }
        return (int) (xp * segments / required);
    }

    public static String render(long xp, long required, int segments, String color) {
        int filled = filledSegments(xp, required, segments);
        return "<color:" + color + ">" + FILLED.repeat(filled) + "</color><dark_gray>"
                + EMPTY.repeat(segments - filled) + "</dark_gray>";
    }
}
