package net.prismarin.api.moderation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record ReportReason(String id, String icon, Map<String, String> names, String color) {

    public ReportReason {
        id = Objects.requireNonNull(id, "id").trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty() || id.contains(" ")) {
            throw new IllegalArgumentException("a reason id needs at least one character and no spaces");
        }
        icon = icon == null || icon.isBlank() ? "PAPER" : icon;
        names = Collections.unmodifiableMap(new LinkedHashMap<>(names));
        color = color == null ? "" : color.trim();
    }

    public String name(Locale locale) {
        return LocalizedText.pick(names, locale).orElse(id);
    }
}
