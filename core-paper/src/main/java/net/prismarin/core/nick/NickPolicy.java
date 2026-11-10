package net.prismarin.core.nick;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record NickPolicy(Where where, When when, boolean autoNick, Set<String> lobbyGroups, Set<String> roundGroups) {

    public NickPolicy {
        lobbyGroups = lowercase(lobbyGroups);
        roundGroups = lowercase(roundGroups);
    }

    public boolean serverAllows(String group, boolean reportsRounds) {
        String normalized = group == null ? "" : group.toLowerCase(Locale.ROOT);
        boolean lobby = lobbyGroups.contains(normalized);
        return switch (where) {
            case EVERYWHERE -> true;
            case NON_LOBBY -> !lobby;
            case ROUND_SERVERS -> roundGroups.contains(normalized) || (reportsRounds && !lobby);
        };
    }

    public boolean shows(boolean serverAllows, boolean inRound) {
        return serverAllows && (when == When.ALWAYS || inRound);
    }

    public Action decide(Moment moment, boolean serverAllows, boolean inRound, boolean hasIdentity,
                         boolean autoWanted) {
        if (!shows(serverAllows, inRound)) {
            return Action.REAL;
        }
        if (hasIdentity) {
            return Action.NICK;
        }
        boolean trigger = moment == Moment.JOIN || moment == Moment.ROUND_START;
        return autoNick && autoWanted && trigger ? Action.GENERATE : Action.REAL;
    }

    private static Set<String> lowercase(Set<String> groups) {
        return groups.stream().map(group -> group.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public enum Where {
        EVERYWHERE("everywhere"),
        NON_LOBBY("non-lobby"),
        ROUND_SERVERS("round-servers");

        private final String key;

        Where(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Optional<Where> parse(String value) {
            return parseKey(values(), value, Where::key);
        }
    }

    public enum When {
        ALWAYS("always"),
        IN_ROUND("in-round");

        private final String key;

        When(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Optional<When> parse(String value) {
            return parseKey(values(), value, When::key);
        }
    }

    public enum Moment {
        JOIN,
        ROUND_START,
        CHECK
    }

    public enum Action {
        NICK,
        GENERATE,
        REAL
    }

    static <E> Optional<E> parseKey(E[] values, String value, Function<E, String> key) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (E candidate : values) {
            if (key.apply(candidate).equals(normalized)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
