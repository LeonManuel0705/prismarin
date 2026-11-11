package net.prismarin.core.vanish;

public final class VanishRules {

    public static final String LEVEL_PREFIX = "prismarin.vanish.level.";

    private VanishRules() {
        throw new AssertionError();
    }

    public static boolean canSee(boolean self, Integer targetLevel, boolean viewerMaySee, int viewerLevel) {
        if (targetLevel == null || self) {
            return true;
        }
        return viewerMaySee && viewerLevel >= targetLevel;
    }

    public static int level(Iterable<String> grantedPermissions) {
        int level = 1;
        for (String permission : grantedPermissions) {
            if (permission == null || !permission.startsWith(LEVEL_PREFIX)) {
                continue;
            }
            try {
                level = Math.max(level, Integer.parseInt(permission.substring(LEVEL_PREFIX.length())));
            } catch (NumberFormatException ignored) {
                continue;
            }
        }
        return level;
    }
}
