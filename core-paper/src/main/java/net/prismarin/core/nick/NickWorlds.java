package net.prismarin.core.nick;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record NickWorlds(boolean enabled, Mode mode, Set<String> worlds, boolean linkDimensions) {

    public enum Mode {
        WHITELIST,
        BLACKLIST;

        public static Mode parse(String value) {
            return "blacklist".equalsIgnoreCase(value == null ? "" : value.trim()) ? BLACKLIST : WHITELIST;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final List<String> DIMENSION_SUFFIXES = List.of("_nether", "_the_end");

    public static final NickWorlds OFF = new NickWorlds(false, Mode.WHITELIST, Set.of(), true);

    public NickWorlds {
        boolean link = linkDimensions;
        worlds = worlds.stream().map(world -> normalize(world, link)).collect(Collectors.toUnmodifiableSet());
    }

    public boolean allows(String world) {
        if (!enabled || world == null) {
            return true;
        }
        boolean listed = worlds.contains(normalize(world, linkDimensions));
        return mode == Mode.WHITELIST ? listed : !listed;
    }

    static String normalize(String world, boolean linkDimensions) {
        String lower = world.trim().toLowerCase(Locale.ROOT);
        if (!linkDimensions) {
            return lower;
        }
        for (String suffix : DIMENSION_SUFFIXES) {
            if (lower.endsWith(suffix) && lower.length() > suffix.length()) {
                return lower.substring(0, lower.length() - suffix.length());
            }
        }
        return lower;
    }
}
