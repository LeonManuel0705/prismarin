package net.prismarin.shared.social;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public final class ClanColors {

    public static final String DEFAULT = "";

    private static final Pattern HEX = Pattern.compile("[0-9a-f]{6}");
    private static final List<String> RESET = List.of("reset", "default", "none", "standard", "normal", "zurueck",
            "zurück");
    private static final String CODES = "0123456789abcdef";
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("grey", "gray"), Map.entry("dark_grey", "dark_gray"), Map.entry("pink", "light_purple"),
            Map.entry("purple", "dark_purple"), Map.entry("magenta", "light_purple"), Map.entry("orange", "gold"),
            Map.entry("cyan", "dark_aqua"), Map.entry("turquoise", "aqua"), Map.entry("light_blue", "aqua"),
            Map.entry("lime", "green"), Map.entry("rot", "red"), Map.entry("dunkelrot", "dark_red"),
            Map.entry("blau", "blue"), Map.entry("dunkelblau", "dark_blue"), Map.entry("gruen", "green"),
            Map.entry("grün", "green"), Map.entry("dunkelgruen", "dark_green"), Map.entry("dunkelgrün", "dark_green"),
            Map.entry("gelb", "yellow"), Map.entry("schwarz", "black"), Map.entry("weiss", "white"),
            Map.entry("weiß", "white"), Map.entry("grau", "gray"), Map.entry("dunkelgrau", "dark_gray"),
            Map.entry("lila", "light_purple"), Map.entry("violett", "dark_purple"), Map.entry("tuerkis", "aqua"),
            Map.entry("türkis", "aqua"));

    private ClanColors() {
        throw new AssertionError();
    }

    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String value = input.strip().toLowerCase(Locale.ROOT);
        if (value.startsWith("<") && value.endsWith(">") && value.length() > 2) {
            value = value.substring(1, value.length() - 1).strip();
        }
        if (value.isEmpty() || RESET.contains(value)) {
            return Optional.of(DEFAULT);
        }
        if ((value.startsWith("&#") || value.startsWith("§#")) && value.length() == 8) {
            value = value.substring(1);
        }
        if (value.startsWith("#")) {
            String hex = value.substring(1);
            return HEX.matcher(hex).matches() ? Optional.of("#" + hex) : Optional.empty();
        }
        if (HEX.matcher(value).matches()) {
            return Optional.of("#" + value);
        }
        if (value.length() == 2 && (value.charAt(0) == '&' || value.charAt(0) == '§')) {
            int index = CODES.indexOf(value.charAt(1));
            if (index < 0) {
                return Optional.empty();
            }
            return Optional.of(named(index));
        }
        String name = value.replace('-', '_').replace(' ', '_');
        name = ALIASES.getOrDefault(name, name);
        return NamedTextColor.NAMES.value(name) == null ? Optional.empty() : Optional.of(name);
    }

    public static Optional<TextColor> color(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return Optional.empty();
        }
        if (normalized.startsWith("#")) {
            return Optional.ofNullable(TextColor.fromHexString(normalized));
        }
        return Optional.ofNullable(NamedTextColor.NAMES.value(normalized));
    }

    public static List<String> suggestions() {
        List<String> names = new ArrayList<>(NamedTextColor.NAMES.keys());
        names.sort(String::compareTo);
        names.add("reset");
        return List.copyOf(names);
    }

    private static String named(int index) {
        return switch (index) {
            case 0 -> "black";
            case 1 -> "dark_blue";
            case 2 -> "dark_green";
            case 3 -> "dark_aqua";
            case 4 -> "dark_red";
            case 5 -> "dark_purple";
            case 6 -> "gold";
            case 7 -> "gray";
            case 8 -> "dark_gray";
            case 9 -> "blue";
            case 10 -> "green";
            case 11 -> "aqua";
            case 12 -> "red";
            case 13 -> "light_purple";
            case 14 -> "yellow";
            default -> "white";
        };
    }
}
