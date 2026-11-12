package net.prismarin.api.joinme;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record JoinMeAudience(Scope scope, Set<String> groups, Set<Circle> circles) {

    public enum Scope {
        NETWORK,
        GROUPS;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Scope> byId(String id) {
            for (Scope scope : values()) {
                if (scope.id().equalsIgnoreCase(id == null ? "" : id.strip())) {
                    return Optional.of(scope);
                }
            }
            return Optional.empty();
        }
    }

    public enum Circle {
        FRIENDS,
        PARTY,
        CLAN;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Circle> byId(String id) {
            for (Circle circle : values()) {
                if (circle.id().equalsIgnoreCase(id == null ? "" : id.strip())) {
                    return Optional.of(circle);
                }
            }
            return Optional.empty();
        }
    }

    public static final JoinMeAudience NETWORK = new JoinMeAudience(Scope.NETWORK, Set.of(), Set.of());

    public JoinMeAudience {
        scope = scope == null ? Scope.NETWORK : scope;
        Set<String> lowered = new LinkedHashSet<>();
        for (String group : groups == null ? Set.<String>of() : groups) {
            if (group != null && !group.isBlank()) {
                lowered.add(group.strip().toLowerCase(Locale.ROOT));
            }
        }
        groups = Set.copyOf(lowered);
        Set<Circle> copied = EnumSet.noneOf(Circle.class);
        for (Circle circle : circles == null ? Set.<Circle>of() : circles) {
            if (circle != null) {
                copied.add(circle);
            }
        }
        circles = Set.copyOf(copied);
    }

    public static JoinMeAudience network() {
        return NETWORK;
    }

    public static JoinMeAudience groups(String... groups) {
        return groups(Arrays.asList(groups));
    }

    public static JoinMeAudience groups(Collection<String> groups) {
        return new JoinMeAudience(Scope.GROUPS, Set.copyOf(Objects.requireNonNull(groups, "groups")), Set.of());
    }

    public JoinMeAudience only(Circle... only) {
        return new JoinMeAudience(scope, groups, Set.of(only));
    }

    public JoinMeAudience only(Collection<Circle> only) {
        return new JoinMeAudience(scope, groups, Set.copyOf(only));
    }

    public boolean social() {
        return !circles.isEmpty();
    }

    public boolean covers(String group) {
        return scope == Scope.NETWORK || group != null && groups.contains(group.toLowerCase(Locale.ROOT));
    }
}
