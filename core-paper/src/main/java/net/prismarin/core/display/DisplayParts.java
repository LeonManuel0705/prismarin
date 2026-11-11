package net.prismarin.core.display;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.shared.text.PrefixStyle;

public final class DisplayParts {

    public static final Pattern PLACEHOLDER = Pattern.compile("[a-z0-9_-]{1,48}");

    private DisplayParts() {
        throw new AssertionError();
    }

    public static Component prefix(List<Component> parts) {
        List<Component> visible = visible(parts);
        if (visible.isEmpty()) {
            return Component.empty();
        }
        return joined(visible).append(Component.space());
    }

    public static Component suffix(List<Component> parts) {
        List<Component> visible = visible(parts);
        if (visible.isEmpty()) {
            return Component.empty();
        }
        return Component.space().append(joined(visible));
    }

    public static Component lines(Component base, List<Component> extra) {
        List<Component> added = extra.stream().filter(Objects::nonNull).toList();
        if (added.isEmpty()) {
            return base;
        }
        Component extraLines = Component.join(JoinConfiguration.newlines(), added);
        if (base == null || PlainTextComponentSerializer.plainText().serialize(base).isEmpty()) {
            return extraLines;
        }
        return base.append(Component.newline()).append(extraLines);
    }

    public static boolean validPlaceholder(String key) {
        return key != null && PLACEHOLDER.matcher(key).matches();
    }

    public static Map<String, Component> placeholders(List<Map<String, Component>> contributions,
                                                      Collection<String> reserved) {
        Set<String> taken = Set.copyOf(reserved);
        Map<String, Component> merged = new LinkedHashMap<>();
        for (Map<String, Component> contribution : contributions) {
            if (contribution == null) {
                continue;
            }
            contribution.forEach((key, value) -> {
                if (value != null && validPlaceholder(key) && !taken.contains(key)) {
                    merged.putIfAbsent(key, value);
                }
            });
        }
        return merged;
    }

    private static List<Component> visible(List<Component> parts) {
        return parts.stream().filter(Objects::nonNull).filter(PrefixStyle::visible).toList();
    }

    private static Component joined(List<Component> parts) {
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        return Component.join(JoinConfiguration.separator(Component.space()), parts);
    }
}
