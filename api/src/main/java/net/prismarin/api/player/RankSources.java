package net.prismarin.api.player;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.prismarin.api.event.Subscription;

public final class RankSources {

    private static final RankSources GLOBAL = new RankSources();

    private record Entry(RankSource source, int priority) {
    }

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    public static RankSources global() {
        return GLOBAL;
    }

    public Subscription register(RankSource source, int priority) {
        Entry entry = new Entry(Objects.requireNonNull(source, "source"), priority);
        entries.add(entry);
        entries.sort(Comparator.comparingInt(Entry::priority).reversed());
        return () -> entries.remove(entry);
    }

    public Subscription register(RankSource source) {
        return register(source, 0);
    }

    public boolean any() {
        return !entries.isEmpty();
    }

    public Optional<RankDisplay> rank(UUID player) {
        for (Entry entry : entries) {
            try {
                Optional<RankDisplay> rank = entry.source().rank(player);
                if (rank != null && rank.isPresent() && rank.get().known()) {
                    return rank;
                }
            } catch (RuntimeException ignored) {
                continue;
            }
        }
        return Optional.empty();
    }
}
