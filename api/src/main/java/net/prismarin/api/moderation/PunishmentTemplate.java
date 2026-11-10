package net.prismarin.api.moderation;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record PunishmentTemplate(String id, Map<String, String> names, Map<String, String> descriptions, String icon,
                                 String permission, List<Step> steps, Duration forgetAfter) {

    public record Step(PunishmentType type, Duration duration) {

        public Step {
            Objects.requireNonNull(type, "type");
            if (!type.lasting()) {
                duration = null;
            }
        }

        public boolean permanent() {
            return type.lasting() && duration == null;
        }
    }

    public PunishmentTemplate {
        id = Objects.requireNonNull(id, "id").trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty() || id.contains(" ")) {
            throw new IllegalArgumentException("a template id needs at least one character and no spaces");
        }
        names = Collections.unmodifiableMap(new LinkedHashMap<>(names));
        descriptions = descriptions == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(descriptions));
        icon = icon == null || icon.isBlank() ? "PAPER" : icon;
        permission = permission == null ? "" : permission.trim();
        steps = List.copyOf(steps);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("template " + id + " needs at least one step");
        }
        if (forgetAfter != null && (forgetAfter.isZero() || forgetAfter.isNegative())) {
            forgetAfter = null;
        }
    }

    public String name(Locale locale) {
        return LocalizedText.pick(names, locale).orElse(id);
    }

    public String description(Locale locale) {
        return LocalizedText.pick(descriptions, locale).orElse("");
    }
}
