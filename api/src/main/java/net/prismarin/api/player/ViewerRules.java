package net.prismarin.api.player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;

public final class ViewerRules {

    public static final int MAX_NAME_LENGTH = 32;

    private ViewerRules() {
        throw new AssertionError();
    }

    public static boolean revealsRealName(UUID viewer, UUID target, boolean privileged) {
        return viewer == null || viewer.equals(target) || privileged;
    }

    public static boolean canSee(UUID viewer, NetworkPlayer target, boolean seesVanished) {
        return !target.vanished() || viewer == null || viewer.equals(target.uuid()) || seesVanished;
    }

    public static String displayName(NetworkPlayer target, boolean revealed) {
        return revealed || !target.nicked() ? target.realName() : target.nick();
    }

    public static RankDisplay rank(NetworkPlayer target, boolean revealed) {
        return revealed ? target.realRank() : target.rank();
    }

    public static boolean plausibleName(String typed) {
        if (typed == null) {
            return false;
        }
        String name = typed.trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return false;
        }
        return name.chars().noneMatch(Character::isWhitespace);
    }

    public static Optional<NetworkPlayer> resolve(Collection<NetworkPlayer> candidates, String typed,
                                                  Predicate<NetworkPlayer> revealed,
                                                  Predicate<NetworkPlayer> visible) {
        if (!plausibleName(typed)) {
            return Optional.empty();
        }
        String name = typed.trim();
        for (NetworkPlayer candidate : candidates) {
            if (candidate.online() && visible.test(candidate) && candidate.shownName().equalsIgnoreCase(name)) {
                return Optional.of(candidate);
            }
        }
        for (NetworkPlayer candidate : candidates) {
            if (candidate.online() && candidate.nicked() && visible.test(candidate) && revealed.test(candidate)
                    && candidate.realName().equalsIgnoreCase(name)) {
                return Optional.of(candidate);
            }
        }
        for (NetworkPlayer candidate : candidates) {
            if (candidate.realName().equalsIgnoreCase(name)) {
                return Optional.of(candidate.online() ? candidate.asOffline() : candidate);
            }
        }
        return Optional.empty();
    }

    public static List<String> suggestions(Collection<NetworkPlayer> candidates, String prefix,
                                           Predicate<NetworkPlayer> revealed, Predicate<NetworkPlayer> visible) {
        String start = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (NetworkPlayer candidate : candidates) {
            if (!candidate.online() || !visible.test(candidate)) {
                continue;
            }
            names.add(candidate.shownName());
            if (candidate.nicked() && revealed.test(candidate)) {
                names.add(candidate.realName());
            }
        }
        return names.stream()
                .filter(name -> !name.isEmpty() && name.toLowerCase(Locale.ROOT).startsWith(start))
                .toList();
    }
}
