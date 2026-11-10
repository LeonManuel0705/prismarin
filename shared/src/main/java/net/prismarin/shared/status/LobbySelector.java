package net.prismarin.shared.status;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public final class LobbySelector {

    private LobbySelector() {
        throw new AssertionError();
    }

    public enum Strategy {
        LEAST_PLAYERS,
        FILL_FIRST,
        RANDOM;

        public String id() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        public static Optional<Strategy> parse(String value) {
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            String normalized = value.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            for (Strategy strategy : values()) {
                if (strategy.name().equals(normalized)) {
                    return Optional.of(strategy);
                }
            }
            return Optional.empty();
        }
    }

    public record Candidate<T>(T target, int players, int softMax, boolean available, boolean premium,
                               boolean permitted) {

        public Candidate {
            Objects.requireNonNull(target, "target");
        }

        public boolean belowSoftMax() {
            return softMax <= 0 || players < softMax;
        }
    }

    public static <T> Optional<T> pick(List<Candidate<T>> candidates, Strategy strategy, boolean includePremium) {
        return pick(candidates, strategy, includePremium, ThreadLocalRandom.current());
    }

    public static <T> Optional<T> pick(List<Candidate<T>> candidates, Strategy strategy, boolean includePremium,
                                       RandomGenerator random) {
        List<Candidate<T>> eligible = candidates.stream()
                .filter(candidate -> candidate.available() && candidate.permitted())
                .filter(candidate -> includePremium || !candidate.premium())
                .toList();
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        Comparator<Candidate<T>> byPlayers = Comparator.comparingInt(Candidate::players);
        List<Candidate<T>> open = eligible.stream().filter(Candidate::belowSoftMax).toList();
        if (open.isEmpty()) {
            return eligible.stream().min(byPlayers).map(Candidate::target);
        }
        return switch (strategy) {
            case LEAST_PLAYERS -> open.stream().min(byPlayers).map(Candidate::target);
            case FILL_FIRST -> Optional.of(open.getFirst().target());
            case RANDOM -> Optional.of(open.get(random.nextInt(open.size())).target());
        };
    }
}
