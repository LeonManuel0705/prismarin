package net.prismarin.api.reward;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.prismarin.api.event.Subscription;

public final class RewardHandlers {

    public static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");

    private static final RewardHandlers GLOBAL = new RewardHandlers();

    private final Map<String, RewardHandler> handlers = new ConcurrentHashMap<>();

    public static RewardHandlers global() {
        return GLOBAL;
    }

    public Subscription register(String id, RewardHandler handler) {
        String key = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
        if (!ID.matcher(key).matches()) {
            throw new IllegalArgumentException("'" + id + "' is no reward id, use " + ID.pattern());
        }
        Objects.requireNonNull(handler, "handler");
        handlers.put(key, handler);
        return () -> handlers.remove(key, handler);
    }

    public Optional<RewardHandler> find(String id) {
        return id == null ? Optional.empty()
                : Optional.ofNullable(handlers.get(id.strip().toLowerCase(Locale.ROOT)));
    }

    public Set<String> ids() {
        return Set.copyOf(new TreeSet<>(handlers.keySet()));
    }
}
