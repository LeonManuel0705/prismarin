package net.prismarin.velocity.admin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record ReportReason(String id, String icon, Map<String, String> names) {

    public ReportReason {
        id = Objects.requireNonNull(id, "id").toLowerCase(Locale.ROOT);
        icon = icon == null || icon.isBlank() ? "PAPER" : icon;
        names = Collections.unmodifiableMap(new LinkedHashMap<>(names));
    }

    public String name(Locale locale) {
        if (locale != null) {
            String exact = names.get(locale.toLanguageTag().toLowerCase(Locale.ROOT));
            if (exact != null) {
                return exact;
            }
            String language = names.get(locale.getLanguage().toLowerCase(Locale.ROOT));
            if (language != null) {
                return language;
            }
        }
        String english = names.get("en");
        if (english != null) {
            return english;
        }
        return names.values().stream().findFirst().orElse(id);
    }
}
