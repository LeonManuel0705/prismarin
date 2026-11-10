package net.prismarin.api.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.prismarin.api.event.Subscription;

public final class PlayerAttributes {

    public static final Pattern KEY = Pattern.compile("[a-z0-9_]{1,48}");

    private static final System.Logger LOGGER = System.getLogger("PRISMARIN");
    private static final Comparator<PlayerAttribute> ORDER = Comparator.comparingInt(PlayerAttribute::order)
            .thenComparing(PlayerAttribute::key);

    private final List<PlayerAttribute> attributes = new CopyOnWriteArrayList<>();
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    public static boolean validKey(String key) {
        return key != null && KEY.matcher(key).matches();
    }

    public Subscription register(PlayerAttribute attribute) {
        Objects.requireNonNull(attribute, "attribute");
        String key = attribute.key();
        if (!validKey(key)) {
            throw new IllegalArgumentException("attribute key '" + key + "' must match " + KEY.pattern());
        }
        synchronized (attributes) {
            if (get(key).isPresent()) {
                throw new IllegalArgumentException("attribute '" + key + "' is already registered");
            }
            attributes.add(attribute);
        }
        reported.remove(key);
        return () -> attributes.remove(attribute);
    }

    public Optional<PlayerAttribute> get(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String wanted = key.toLowerCase(Locale.ROOT);
        return attributes.stream().filter(attribute -> attribute.key().equals(wanted)).findFirst();
    }

    public List<PlayerAttribute> all() {
        List<PlayerAttribute> sorted = new ArrayList<>(attributes);
        sorted.sort(ORDER);
        return List.copyOf(sorted);
    }

    public Optional<String> value(String key, UUID viewer, NetworkPlayer target) {
        return get(key).flatMap(attribute -> value(attribute, viewer, target));
    }

    public Map<String, String> values(UUID viewer, NetworkPlayer target) {
        Map<String, String> values = new LinkedHashMap<>();
        for (PlayerAttribute attribute : all()) {
            value(attribute, viewer, target).ifPresent(value -> values.put(attribute.key(), value));
        }
        return values;
    }

    public Optional<Component> profileLine(String key, UUID viewer, NetworkPlayer target, Locale locale) {
        return get(key).flatMap(attribute -> profileLine(attribute, viewer, target, locale));
    }

    public List<Component> profileLines(UUID viewer, NetworkPlayer target, Locale locale, Collection<String> skipped) {
        List<Component> lines = new ArrayList<>();
        for (PlayerAttribute attribute : all()) {
            if (skipped.contains(attribute.key())) {
                continue;
            }
            profileLine(attribute, viewer, target, locale).ifPresent(lines::add);
        }
        return List.copyOf(lines);
    }

    private Optional<String> value(PlayerAttribute attribute, UUID viewer, NetworkPlayer target) {
        return guarded(attribute, () -> attribute.value(viewer, target));
    }

    private Optional<Component> profileLine(PlayerAttribute attribute, UUID viewer, NetworkPlayer target,
                                            Locale locale) {
        return guarded(attribute, () -> attribute.profileLine(viewer, target, locale));
    }

    private <T> Optional<T> guarded(PlayerAttribute attribute, Supplier<Optional<T>> call) {
        try {
            Optional<T> result = call.get();
            return result == null ? Optional.empty() : result;
        } catch (RuntimeException | LinkageError e) {
            if (reported.add(attribute.key())) {
                LOGGER.log(System.Logger.Level.WARNING, "Player attribute '" + attribute.key() + "' failed", e);
            }
            return Optional.empty();
        }
    }
}
