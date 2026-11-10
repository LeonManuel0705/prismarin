package net.prismarin.velocity.admin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.shared.text.LegacyColors;

public record ReportReason(String id, String icon, Map<String, String> names, TextColor color) {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    public ReportReason {
        id = Objects.requireNonNull(id, "id").toLowerCase(Locale.ROOT);
        icon = icon == null || icon.isBlank() ? "PAPER" : icon;
        names = Collections.unmodifiableMap(new LinkedHashMap<>(names));
    }

    public ReportReason(String id, String icon, Map<String, String> names) {
        this(id, icon, names, null);
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

    public Component display(Locale locale) {
        Component name = styled(name(locale));
        return color == null ? name : name.colorIfAbsent(color);
    }

    public String plain(Locale locale) {
        return plain(name(locale));
    }

    public static Component describe(String stored, List<ReportReason> reasons, Locale locale) {
        String text = stored == null ? "" : stored;
        ReportReason best = null;
        int length = -1;
        for (ReportReason reason : reasons) {
            for (String name : reason.names().values()) {
                String label = plain(name);
                if (label.length() > length && labels(text, label)) {
                    best = reason;
                    length = label.length();
                }
            }
        }
        if (best == null) {
            return Component.text(text);
        }
        String rest = text.substring(length);
        return rest.isEmpty() ? best.display(locale) : best.display(locale).append(Component.text(rest));
    }

    static boolean labels(String text, String label) {
        if (label.isEmpty() || !text.regionMatches(true, 0, label, 0, label.length())) {
            return false;
        }
        return text.length() == label.length() || text.charAt(label.length()) == ':';
    }

    private static Component styled(String raw) {
        return MINI.deserialize(LegacyColors.toMiniMessage(raw));
    }

    private static String plain(String raw) {
        return PlainTextComponentSerializer.plainText().serialize(styled(raw)).strip();
    }
}
