package net.prismarin.lobby.util;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class Languages {

    private Languages() {
        throw new AssertionError();
    }

    public static String resolve(Collection<String> languages, Locale locale, String fallback) {
        String normalizedFallback = fallback == null ? "" : fallback.toLowerCase(Locale.ROOT);
        if (locale != null && !locale.getLanguage().isEmpty()) {
            String tag = locale.toLanguageTag().toLowerCase(Locale.ROOT);
            if (languages.contains(tag)) {
                return tag;
            }
            String language = locale.getLanguage().toLowerCase(Locale.ROOT);
            if (languages.contains(language)) {
                return language;
            }
        }
        if (languages.contains(normalizedFallback) || languages.isEmpty()) {
            return normalizedFallback;
        }
        return languages.iterator().next();
    }

    public static Optional<String> toggleTarget(List<String> languages, String current) {
        if (languages.size() != 2) {
            return Optional.empty();
        }
        return Optional.of(languages.get(0).equals(current) ? languages.get(1) : languages.get(0));
    }
}
