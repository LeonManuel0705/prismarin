package net.prismarin.velocity.admin;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.prismarin.shared.text.LegacyColors;

public final class TextColors {

    private static final Map<String, TextColor> ALIASES = Map.of(
            "orange", TextColor.color(0xFF8C00),
            "grey", NamedTextColor.GRAY,
            "dark_grey", NamedTextColor.DARK_GRAY);

    private TextColors() {
        throw new AssertionError();
    }

    public static Optional<TextColor> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String text = value.strip();
        if (text.startsWith("&") || text.startsWith("§")) {
            text = LegacyColors.toMiniMessage(text);
        }
        text = text.toLowerCase(Locale.ROOT);
        if (text.startsWith("<") && text.endsWith(">")) {
            text = text.substring(1, text.length() - 1).strip();
        }
        for (String prefix : new String[] {"color:", "colour:", "c:"}) {
            if (text.startsWith(prefix)) {
                text = text.substring(prefix.length()).strip();
                break;
            }
        }
        if (text.isEmpty()) {
            return Optional.empty();
        }
        if (text.startsWith("#")) {
            return hex(text.substring(1));
        }
        String name = text.replace(' ', '_').replace('-', '_');
        NamedTextColor named = NamedTextColor.NAMES.value(name);
        if (named != null) {
            return Optional.of(named);
        }
        return Optional.ofNullable(ALIASES.get(name));
    }

    private static Optional<TextColor> hex(String digits) {
        String full = digits.length() == 3
                ? "" + digits.charAt(0) + digits.charAt(0) + digits.charAt(1) + digits.charAt(1)
                + digits.charAt(2) + digits.charAt(2)
                : digits;
        if (full.length() != 6) {
            return Optional.empty();
        }
        for (int i = 0; i < full.length(); i++) {
            if (Character.digit(full.charAt(i), 16) < 0) {
                return Optional.empty();
            }
        }
        return Optional.of(TextColor.color(Integer.parseInt(full, 16)));
    }
}
