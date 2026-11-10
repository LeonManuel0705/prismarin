package net.prismarin.core.bridge;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public final class LegacyCodes {

    private static final char SECTION = '§';
    private static final List<NamedTextColor> ORDER = List.of(NamedTextColor.BLACK, NamedTextColor.DARK_BLUE,
            NamedTextColor.DARK_GREEN, NamedTextColor.DARK_AQUA, NamedTextColor.DARK_RED, NamedTextColor.DARK_PURPLE,
            NamedTextColor.GOLD, NamedTextColor.GRAY, NamedTextColor.DARK_GRAY, NamedTextColor.BLUE,
            NamedTextColor.GREEN, NamedTextColor.AQUA, NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE,
            NamedTextColor.YELLOW, NamedTextColor.WHITE);
    private static final String DIGITS = "0123456789abcdef";

    private LegacyCodes() {
        throw new AssertionError();
    }

    public static String color(TextColor color) {
        if (color == null) {
            return "";
        }
        NamedTextColor named = color instanceof NamedTextColor exact ? exact : NamedTextColor.namedColor(color.value());
        int index = named == null ? -1 : ORDER.indexOf(named);
        if (index >= 0) {
            return String.valueOf(SECTION) + DIGITS.charAt(index);
        }
        StringBuilder code = new StringBuilder().append(SECTION).append('x');
        for (char digit : String.format(Locale.ROOT, "%06x", color.value()).toCharArray()) {
            code.append(SECTION).append(digit);
        }
        return code.toString();
    }
}
