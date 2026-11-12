package net.prismarin.velocity.joinme;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public record Tiers<T>(String permissionPrefix, Map<String, T> values) {

    public static final String DEFAULT = "default";

    public Tiers {
        permissionPrefix = permissionPrefix == null ? "" : permissionPrefix;
        Map<String, T> copied = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((name, value) -> {
                if (name != null && value != null) {
                    copied.put(name.toLowerCase(Locale.ROOT), value);
                }
            });
        }
        values = Collections.unmodifiableMap(copied);
    }

    public String permission(String name) {
        return permissionPrefix + name.toLowerCase(Locale.ROOT);
    }

    public Optional<T> best(Predicate<String> hasPermission, Comparator<? super T> better) {
        T best = null;
        for (Map.Entry<String, T> entry : values.entrySet()) {
            boolean granted = DEFAULT.equals(entry.getKey()) || hasPermission.test(permission(entry.getKey()));
            if (granted && (best == null || better.compare(entry.getValue(), best) < 0)) {
                best = entry.getValue();
            }
        }
        return Optional.ofNullable(best);
    }
}
