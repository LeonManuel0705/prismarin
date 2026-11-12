package net.prismarin.core.stats;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.prismarin.api.stats.StatFormat;
import net.prismarin.shared.stats.StatNumbers;

public final class PlaceholderStats {

    public interface Resolver {

        boolean available(String identifier);

        String resolve(UUID player, String text);
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("%([A-Za-z0-9]+)_[^%\\s]+%");

    private final Map<String, String> placeholders;
    private final Function<String, StatFormat> formats;
    private final StatNumbers.Decimal decimal;

    public PlaceholderStats(Map<String, String> placeholders, Function<String, StatFormat> formats,
                            StatNumbers.Decimal decimal) {
        this.placeholders = Collections.unmodifiableMap(new LinkedHashMap<>(placeholders));
        this.formats = formats;
        this.decimal = decimal == null ? StatNumbers.Decimal.AUTO : decimal;
    }

    public static Optional<String> identifier(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        return matcher.find() ? Optional.of(matcher.group(1).toLowerCase(Locale.ROOT)) : Optional.empty();
    }

    public boolean empty() {
        return placeholders.isEmpty();
    }

    public Map<String, String> placeholders() {
        return placeholders;
    }

    public Map<String, Long> read(UUID player, Resolver resolver) {
        if (resolver == null || placeholders.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> values = new LinkedHashMap<>();
        placeholders.forEach((key, text) -> value(player, key, text, resolver)
                .ifPresent(value -> values.put(key, value)));
        return Collections.unmodifiableMap(values);
    }

    private OptionalLong value(UUID player, String key, String text, Resolver resolver) {
        Optional<String> identifier = identifier(text);
        if (identifier.isEmpty()) {
            return OptionalLong.empty();
        }
        String resolved;
        try {
            if (!resolver.available(identifier.get())) {
                return OptionalLong.empty();
            }
            resolved = resolver.resolve(player, text);
        } catch (RuntimeException e) {
            return OptionalLong.empty();
        }
        if (resolved == null || resolved.isBlank() || unresolved(resolved, identifier.get())) {
            return OptionalLong.empty();
        }
        return StatNumbers.parse(resolved, formats.apply(key), decimal);
    }

    private static boolean unresolved(String resolved, String identifier) {
        return resolved.toLowerCase(Locale.ROOT).contains("%" + identifier + "_");
    }
}
