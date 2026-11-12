package net.prismarin.api.joinme;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public sealed interface JoinMeScope {

    boolean covers(JoinMeSpot spot);

    int specificity();

    static JoinMeScope server() {
        return Server.INSTANCE;
    }

    static JoinMeScope worlds(String... worlds) {
        return worlds(Arrays.asList(worlds));
    }

    static JoinMeScope worlds(Collection<String> worlds) {
        return new Worlds(Set.copyOf(worlds));
    }

    static JoinMeScope groups(String... groups) {
        return new Groups(Set.copyOf(Arrays.asList(groups)));
    }

    static JoinMeScope when(Predicate<JoinMeSpot> test) {
        return new Matching(test);
    }

    record Server() implements JoinMeScope {

        static final Server INSTANCE = new Server();

        @Override
        public boolean covers(JoinMeSpot spot) {
            return true;
        }

        @Override
        public int specificity() {
            return 0;
        }
    }

    record Groups(Set<String> groups) implements JoinMeScope {

        public Groups {
            groups = lowered(groups);
        }

        @Override
        public boolean covers(JoinMeSpot spot) {
            return groups.contains(spot.group().toLowerCase(Locale.ROOT));
        }

        @Override
        public int specificity() {
            return 1;
        }
    }

    record Worlds(Set<String> worlds) implements JoinMeScope {

        public Worlds {
            worlds = lowered(worlds);
        }

        @Override
        public boolean covers(JoinMeSpot spot) {
            return worlds.contains(spot.world().toLowerCase(Locale.ROOT));
        }

        @Override
        public int specificity() {
            return 2;
        }
    }

    record Matching(Predicate<JoinMeSpot> test) implements JoinMeScope {

        public Matching {
            Objects.requireNonNull(test, "test");
        }

        @Override
        public boolean covers(JoinMeSpot spot) {
            return test.test(spot);
        }

        @Override
        public int specificity() {
            return 3;
        }
    }

    private static Set<String> lowered(Set<String> values) {
        Set<String> lowered = new LinkedHashSet<>();
        for (String value : Objects.requireNonNull(values, "values")) {
            if (value != null && !value.isBlank()) {
                lowered.add(value.strip().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(lowered);
    }
}
