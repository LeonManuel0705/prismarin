package net.prismarin.core.tablist;

public final class TeamSortKeys {

    private static final int BASE_ORDER = 1000;
    private static final int MAX_ORDER = 9999;
    private static final int MAX_LENGTH = 16;

    private TeamSortKeys() {
        throw new AssertionError();
    }

    public static String sortKey(int weight, String name) {
        int order = Math.clamp((long) BASE_ORDER - weight, 0, MAX_ORDER);
        String key = "%04d".formatted(order) + name;
        return key.length() <= MAX_LENGTH ? key : key.substring(0, MAX_LENGTH);
    }
}
