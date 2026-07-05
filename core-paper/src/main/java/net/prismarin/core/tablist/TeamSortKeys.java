package net.prismarin.core.tablist;

public final class TeamSortKeys {

    private static final int BASE_ORDER = 1000;
    private static final int MAX_ORDER = 9999;
    private static final String MANAGED_PREFIX = "psm";
    private static final int ORDER_DIGITS = 4;

    private TeamSortKeys() {
        throw new AssertionError();
    }

    public static String sortKey(int weight, String name) {
        int order = Math.clamp((long) BASE_ORDER - weight, 0, MAX_ORDER);
        return MANAGED_PREFIX + "%04d".formatted(order) + name;
    }

    public static boolean isManaged(String teamName) {
        if (teamName.length() < MANAGED_PREFIX.length() + ORDER_DIGITS
                || !teamName.startsWith(MANAGED_PREFIX)) {
            return false;
        }
        for (int i = MANAGED_PREFIX.length(); i < MANAGED_PREFIX.length() + ORDER_DIGITS; i++) {
            if (!Character.isDigit(teamName.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
