package net.prismarin.api.moderation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ReportAction(String id, String icon, Map<String, String> names, String permission, boolean closeMenu,
                           Handler handler) {

    public static final UUID CONSOLE = new UUID(0L, 0L);

    @FunctionalInterface
    public interface Handler {

        void run(UUID staff, String staffName, Report report);
    }

    public ReportAction {
        id = Objects.requireNonNull(id, "id").trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty() || id.contains(" ") || id.contains(":")) {
            throw new IllegalArgumentException("an action id needs at least one character, no spaces and no colon");
        }
        icon = icon == null || icon.isBlank() ? "PAPER" : icon;
        names = Collections.unmodifiableMap(new LinkedHashMap<>(names));
        permission = permission == null ? "" : permission.trim();
        Objects.requireNonNull(handler, "handler");
    }

    public String name(Locale locale) {
        return LocalizedText.pick(names, locale).orElse(id);
    }
}
