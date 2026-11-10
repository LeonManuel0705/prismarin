package net.prismarin.velocity.admin;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.shared.moderation.Durations;

public record PunishmentTemplate(String id, Map<String, String> names, String icon, String permission,
                                 List<Step> steps, Duration forgetAfter) {

    private static final Set<String> PERMANENT_WORDS = Set.of("permanent", "perm", "forever", "never");

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

        public PunishmentKind kind() {
            return PunishmentKind.of(type, permanent(), false);
        }
    }

    public PunishmentTemplate {
        id = Objects.requireNonNull(id, "id").toLowerCase(Locale.ROOT);
        names = Collections.unmodifiableMap(new LinkedHashMap<>(names));
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

    public Step step(int previousOffences) {
        return steps.get(Math.max(0, Math.min(previousOffences, steps.size() - 1)));
    }

    public int stepIndex(int previousOffences) {
        return Math.max(0, Math.min(previousOffences, steps.size() - 1));
    }

    public boolean restricted() {
        return !permission.isEmpty();
    }

    public static Optional<Step> parseStep(String text, PunishmentType fallback) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String[] words = text.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (words.length > 2) {
            return Optional.empty();
        }
        Optional<PunishmentKind> kind = PunishmentKind.parse(words[0]);
        if (kind.isEmpty()) {
            return words.length == 1 && fallback != null ? length(fallback, words[0], false) : Optional.empty();
        }
        PunishmentType type = kind.get().type();
        boolean needsDuration = kind.get() == PunishmentKind.TEMPBAN || kind.get() == PunishmentKind.TEMPMUTE;
        if (kind.get() == PunishmentKind.IPBAN) {
            return Optional.empty();
        }
        if (!type.lasting()) {
            return words.length == 1 ? Optional.of(new Step(type, null)) : Optional.empty();
        }
        if (words.length == 1) {
            return needsDuration ? Optional.empty() : Optional.of(new Step(type, null));
        }
        return length(type, words[1], needsDuration);
    }

    private static Optional<Step> length(PunishmentType type, String word, boolean needsDuration) {
        if (!type.lasting()) {
            return Optional.empty();
        }
        if (PERMANENT_WORDS.contains(word)) {
            return needsDuration ? Optional.empty() : Optional.of(new Step(type, null));
        }
        return Durations.parse(word).map(duration -> new Step(type, duration));
    }
}
