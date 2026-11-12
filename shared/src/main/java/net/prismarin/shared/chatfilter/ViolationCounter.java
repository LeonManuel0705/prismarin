package net.prismarin.shared.chatfilter;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ViolationCounter {

    public record Trigger(int violations, List<String> evidence) {

        public Trigger {
            evidence = List.copyOf(evidence);
        }
    }

    private record Key(UUID player, String rule) {
    }

    private record Hit(long at, String line) {
    }

    private static final class Hits {

        private final Deque<Hit> hits = new ArrayDeque<>();
        private long window;
    }

    private static final int MAX_EVIDENCE = 20;
    private static final int CLEANUP_SIZE = 5_000;

    private final Map<Key, Hits> counters = new HashMap<>();
    private final Clock clock;

    public ViolationCounter(Clock clock) {
        this.clock = clock;
    }

    public synchronized Optional<Trigger> record(UUID player, String rule, int threshold, long windowMillis,
                                                 String line) {
        long now = clock.millis();
        if (counters.size() > CLEANUP_SIZE) {
            cleanup(now);
        }
        Key key = new Key(player, rule);
        Hits entry = counters.computeIfAbsent(key, ignored -> new Hits());
        entry.window = windowMillis;
        prune(entry, now);
        entry.hits.addLast(new Hit(now, line == null ? "" : line));
        if (entry.hits.size() < Math.max(1, threshold)) {
            return Optional.empty();
        }
        counters.remove(key);
        List<String> lines = entry.hits.stream().map(Hit::line).filter(text -> !text.isEmpty()).toList();
        return Optional.of(new Trigger(entry.hits.size(),
                lines.subList(Math.max(0, lines.size() - MAX_EVIDENCE), lines.size())));
    }

    public synchronized int count(UUID player, String rule) {
        Hits entry = counters.get(new Key(player, rule));
        if (entry == null) {
            return 0;
        }
        prune(entry, clock.millis());
        return entry.hits.size();
    }

    public synchronized void reset(UUID player) {
        counters.keySet().removeIf(key -> key.player().equals(player));
    }

    private static void prune(Hits entry, long now) {
        while (!entry.hits.isEmpty() && entry.hits.peekFirst().at() <= now - entry.window) {
            entry.hits.pollFirst();
        }
    }

    private void cleanup(long now) {
        counters.values().removeIf(entry -> {
            prune(entry, now);
            return entry.hits.isEmpty();
        });
    }
}
