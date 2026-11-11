package net.prismarin.lobby.extension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import net.prismarin.api.event.Subscription;

public final class EntryRegistry<T> {

    public static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,48}");

    private static final System.Logger LOGGER = System.getLogger("PRISMARIN");

    private final String kind;
    private final Function<? super T, String> ids;
    private final ToIntFunction<? super T> orders;
    private final Function<? super T, String> problems;
    private final Set<String> reported = ConcurrentHashMap.newKeySet();
    private volatile List<T> entries = List.of();

    public EntryRegistry(String kind, Function<? super T, String> ids, ToIntFunction<? super T> orders,
                         Function<? super T, String> problems) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.orders = Objects.requireNonNull(orders, "orders");
        this.problems = Objects.requireNonNull(problems, "problems");
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    public Subscription register(T entry) {
        Objects.requireNonNull(entry, kind);
        String id = ids.apply(entry);
        if (!validId(id)) {
            throw new IllegalArgumentException(kind + " id '" + id + "' must match " + ID.pattern());
        }
        String problem = problems.apply(entry);
        if (problem != null) {
            throw new IllegalArgumentException(kind + " '" + id + "': " + problem);
        }
        synchronized (this) {
            if (get(id).isPresent()) {
                throw new IllegalArgumentException(kind + " '" + id + "' is already registered");
            }
            List<T> updated = new ArrayList<>(entries);
            updated.add(entry);
            updated.sort(Comparator.comparingInt(orders));
            entries = List.copyOf(updated);
        }
        reported.remove(id);
        return () -> remove(entry);
    }

    public List<T> all() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Optional<T> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (T entry : entries) {
            if (id.equals(ids.apply(entry))) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public <R> Optional<R> call(T entry, Supplier<? extends R> action) {
        try {
            return Optional.ofNullable(action.get());
        } catch (RuntimeException | LinkageError e) {
            String id = ids.apply(entry);
            if (reported.add(id)) {
                LOGGER.log(System.Logger.Level.WARNING, "The " + kind + " '" + id + "' of another plugin failed", e);
            }
            return Optional.empty();
        }
    }

    private synchronized void remove(T entry) {
        List<T> updated = new ArrayList<>(entries);
        if (updated.remove(entry)) {
            entries = List.copyOf(updated);
        }
    }
}
