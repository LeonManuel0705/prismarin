package net.prismarin.core.display;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.prismarin.api.event.Subscription;

public final class PriorityRegistry<T> {

    private static final System.Logger LOGGER = System.getLogger("PRISMARIN");

    private record Entry<T>(T value, int priority, long sequence) {
    }

    private final String kind;
    private final AtomicLong sequence = new AtomicLong();
    private final Set<Object> reported = ConcurrentHashMap.newKeySet();
    private volatile List<Entry<T>> entries = List.of();

    public PriorityRegistry(String kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public Subscription register(T value, int priority) {
        Objects.requireNonNull(value, kind);
        Entry<T> entry = new Entry<>(value, priority, sequence.getAndIncrement());
        synchronized (this) {
            List<Entry<T>> updated = new ArrayList<>(entries);
            updated.add(entry);
            updated.sort(Comparator.comparingInt((Entry<T> item) -> item.priority()).reversed()
                    .thenComparingLong(Entry::sequence));
            entries = List.copyOf(updated);
        }
        return () -> remove(entry);
    }

    public List<T> values() {
        return entries.stream().map(Entry::value).toList();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    public Optional<T> first(Predicate<? super T> filter) {
        for (Entry<T> entry : entries) {
            if (call(entry.value(), () -> filter.test(entry.value())).orElse(false)) {
                return Optional.of(entry.value());
            }
        }
        return Optional.empty();
    }

    public <R> List<R> collect(Function<? super T, ? extends R> mapper) {
        List<Entry<T>> current = entries;
        if (current.isEmpty()) {
            return List.of();
        }
        List<R> results = new ArrayList<>(current.size());
        for (Entry<T> entry : current) {
            call(entry.value(), () -> mapper.apply(entry.value())).ifPresent(results::add);
        }
        return results;
    }

    public <R> List<R> collectAll(Function<? super T, ? extends List<? extends R>> mapper) {
        List<Entry<T>> current = entries;
        if (current.isEmpty()) {
            return List.of();
        }
        List<R> results = new ArrayList<>();
        for (Entry<T> entry : current) {
            call(entry.value(), () -> mapper.apply(entry.value())).ifPresent(list -> list.stream()
                    .filter(Objects::nonNull)
                    .forEach(results::add));
        }
        return results;
    }

    public <R> Optional<R> call(T value, Supplier<? extends R> action) {
        try {
            return Optional.ofNullable(action.get());
        } catch (RuntimeException | LinkageError e) {
            if (reported.add(value)) {
                LOGGER.log(System.Logger.Level.WARNING, "A " + kind + " of another plugin failed ("
                        + value.getClass().getName() + ")", e);
            }
            return Optional.empty();
        }
    }

    private synchronized void remove(Entry<T> entry) {
        List<Entry<T>> updated = new ArrayList<>(entries);
        if (updated.remove(entry)) {
            entries = List.copyOf(updated);
        }
        reported.remove(entry.value());
    }
}
