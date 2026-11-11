package net.prismarin.shared.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class PrefixStyle {

    private static final Pattern TOKEN = Pattern.compile(
            "[&\u00a7]x(?:[&\u00a7][0-9a-fA-F]){6}|&#[0-9a-fA-F]{6}|[&\u00a7][0-9a-fA-Fk-orK-OR]|<[#a-zA-Z][^<>]*>");
    private static final Pattern LEGACY_CODE = Pattern.compile("^[&§]([0-9a-fA-F])$");
    private static final Pattern LEGACY_HEX = Pattern.compile("^&?#([0-9a-fA-F]{6})$");
    private static final String LEGACY_DIGITS = "0123456789abcdef";
    private static final NamedTextColor[] LEGACY_COLORS = {
            NamedTextColor.BLACK, NamedTextColor.DARK_BLUE, NamedTextColor.DARK_GREEN, NamedTextColor.DARK_AQUA,
            NamedTextColor.DARK_RED, NamedTextColor.DARK_PURPLE, NamedTextColor.GOLD, NamedTextColor.GRAY,
            NamedTextColor.DARK_GRAY, NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.AQUA,
            NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE, NamedTextColor.YELLOW, NamedTextColor.WHITE};

    private PrefixStyle() {
        throw new AssertionError();
    }

    public static Component trimTrailing(Component component, String separators) {
        if (component == null) {
            return Component.empty();
        }
        List<Segment> segments = new ArrayList<>();
        flatten(component, Style.empty(), segments);
        int end = segments.size();
        while (end > 0) {
            Segment last = segments.get(end - 1);
            if (last.opaque() != null) {
                break;
            }
            String text = last.text();
            int cut = text.length();
            while (cut > 0) {
                int codePoint = text.codePointBefore(cut);
                if (!Character.isWhitespace(codePoint) && separators.indexOf(codePoint) < 0) {
                    break;
                }
                cut -= Character.charCount(codePoint);
            }
            if (cut > 0) {
                segments.set(end - 1, new Segment(text.substring(0, cut), last.style(), null));
                break;
            }
            end--;
        }
        TextComponent.Builder builder = Component.text();
        boolean shown = false;
        for (Segment segment : segments.subList(0, end)) {
            if (segment.opaque() != null) {
                builder.append(segment.opaque());
                shown = true;
            } else {
                builder.append(Component.text(segment.text(), segment.style()));
                shown |= !segment.text().isBlank();
            }
        }
        return shown ? builder.build() : component;
    }

    private static void flatten(Component component, Style parent, List<Segment> out) {
        Style style = component.style().merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TextComponent text) {
            if (!text.content().isEmpty()) {
                out.add(new Segment(text.content(), style, null));
            }
        } else {
            out.add(new Segment("", style, component.children(List.of()).style(style)));
        }
        for (Component child : component.children()) {
            flatten(child, style, out);
        }
    }

    private record Segment(String text, Style style, Component opaque) {
    }

    public static boolean visible(Component prefix) {
        return !PlainTextComponentSerializer.plainText().serialize(prefix).isBlank();
    }

    public static Optional<TextColor> lastColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        TextColor last = null;
        Matcher matcher = TOKEN.matcher(raw);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() == 2 && (token.charAt(0) == '&' || token.charAt(0) == '\u00a7')) {
                char code = Character.toLowerCase(token.charAt(1));
                int index = LEGACY_DIGITS.indexOf(code);
                if (index >= 0) {
                    last = LEGACY_COLORS[index];
                } else if (code == 'r') {
                    last = null;
                }
            } else if (token.startsWith("&x") || token.startsWith("\u00a7x")) {
                last = TextColor.fromHexString("#" + token.replaceAll("[&\u00a7x]", ""));
            } else if (token.startsWith("&#")) {
                last = TextColor.fromHexString(token.substring(1));
            } else {
                last = tagColor(token.substring(1, token.length() - 1)).orElse(last);
            }
        }
        return Optional.ofNullable(last);
    }

    private static Optional<TextColor> tagColor(String tag) {
        String lower = tag.toLowerCase(Locale.ROOT);
        if (lower.equals("reset")) {
            return Optional.empty();
        }
        if (lower.startsWith("#")) {
            return Optional.ofNullable(TextColor.fromHexString(lower));
        }
        if (lower.startsWith("color:") || lower.startsWith("colour:") || lower.startsWith("c:")) {
            return parseName(lower.substring(lower.indexOf(':') + 1));
        }
        if (lower.startsWith("gradient:")) {
            String[] parts = lower.split(":");
            return parseName(parts[parts.length - 1]);
        }
        return Optional.ofNullable(NamedTextColor.NAMES.value(lower));
    }

    private static Optional<TextColor> parseName(String value) {
        if (value.startsWith("#")) {
            return Optional.ofNullable(TextColor.fromHexString(value));
        }
        return Optional.ofNullable(NamedTextColor.NAMES.value(value));
    }

    public static Optional<TextColor> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        Matcher code = LEGACY_CODE.matcher(trimmed);
        if (code.matches()) {
            return Optional.of(LEGACY_COLORS[LEGACY_DIGITS.indexOf(code.group(1).toLowerCase(Locale.ROOT))]);
        }
        Matcher hex = LEGACY_HEX.matcher(trimmed);
        if (hex.matches()) {
            return Optional.ofNullable(TextColor.fromHexString("#" + hex.group(1)));
        }
        NamedTextColor named = NamedTextColor.NAMES.value(trimmed.toLowerCase(Locale.ROOT).replace('-', '_'));
        if (named != null) {
            return Optional.of(named);
        }
        return lastColor(trimmed);
    }

    public static NamedTextColor nearest(TextColor color) {
        return color instanceof NamedTextColor named ? named : NamedTextColor.nearestTo(color);
    }

}
