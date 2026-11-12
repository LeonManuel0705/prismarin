package net.prismarin.shared.joinme;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeHandler;
import net.prismarin.api.joinme.JoinMeScope;
import net.prismarin.api.joinme.JoinMeSpot;

public final class JoinMeHandlerRegistry {

    public static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_.:-]{0,47}");

    private record Entry(long order, JoinMeScope scope, JoinMeHandler handler) {
    }

    private static final Comparator<Entry> ORDER = Comparator
            .comparingInt((Entry entry) -> priority(entry.handler())).reversed()
            .thenComparing(Comparator.comparingInt((Entry entry) -> entry.scope().specificity()).reversed())
            .thenComparingLong(Entry::order);

    private final List<Entry> entries = new CopyOnWriteArrayList<>();
    private final AtomicLong counter = new AtomicLong();

    public Subscription register(JoinMeScope scope, JoinMeHandler handler) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(handler, "handler");
        String id = handler.id();
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("'" + id + "' is no JoinMe handler id, use " + ID.pattern());
        }
        Entry entry = new Entry(counter.incrementAndGet(), scope, handler);
        entries.add(entry);
        return () -> entries.remove(entry);
    }

    public Optional<JoinMeHandler> select(JoinMeSpot spot) {
        return entries.stream().filter(entry -> covers(entry, spot)).sorted(ORDER).map(Entry::handler).findFirst();
    }

    public Optional<JoinMeHandler> forArrival(JoinMe joinMe, JoinMeSpot current) {
        if (joinMe.handled()) {
            Optional<JoinMeHandler> recorded = entries.stream()
                    .filter(entry -> entry.handler().id().equals(joinMe.handler()))
                    .sorted(ORDER)
                    .map(Entry::handler)
                    .findFirst();
            if (recorded.isPresent()) {
                return recorded;
            }
        }
        return select(current);
    }

    public List<JoinMeHandler> handlers() {
        return entries.stream().sorted(ORDER).map(Entry::handler).toList();
    }

    public int size() {
        return entries.size();
    }

    private static boolean covers(Entry entry, JoinMeSpot spot) {
        try {
            return entry.scope().covers(spot);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static int priority(JoinMeHandler handler) {
        try {
            return handler.priority();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
