package net.prismarin.shared.text;

import java.util.Locale;
import java.util.Map;

public final class LegacyColors {

    private static final Map<Character, String> COLORS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"));

    private static final Map<Character, String> DECORATIONS = Map.of(
            'k', "obfuscated", 'l', "bold", 'm', "strikethrough", 'n', "underlined", 'o', "italic");

    private LegacyColors() {
        throw new AssertionError();
    }

    public static String toMiniMessage(String input) {
        if (input.indexOf('&') < 0 && input.indexOf('§') < 0) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        boolean decorated = false;
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                String hex = hexAt(input, i);
                if (hex != null) {
                    out.append(decorated ? "<reset>" : "").append("<#").append(hex).append('>');
                    decorated = false;
                    i += input.charAt(i + 1) == '#' ? 8 : 14;
                    continue;
                }
                if (COLORS.containsKey(code)) {
                    out.append(decorated ? "<reset>" : "").append('<').append(COLORS.get(code)).append('>');
                    decorated = false;
                    i += 2;
                    continue;
                }
                if (DECORATIONS.containsKey(code)) {
                    out.append('<').append(DECORATIONS.get(code)).append('>');
                    decorated = true;
                    i += 2;
                    continue;
                }
                if (code == 'r') {
                    out.append("<reset>");
                    decorated = false;
                    i += 2;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String hexAt(String input, int start) {
        if (input.charAt(start + 1) == '#' && start + 8 <= input.length()) {
            String hex = input.substring(start + 2, start + 8);
            return isHex(hex) ? hex.toUpperCase(Locale.ROOT) : null;
        }
        char marker = input.charAt(start);
        if (Character.toLowerCase(input.charAt(start + 1)) == 'x' && start + 14 <= input.length()) {
            StringBuilder hex = new StringBuilder(6);
            for (int pos = start + 2; pos < start + 14; pos += 2) {
                if (input.charAt(pos) != marker) {
                    return null;
                }
                hex.append(input.charAt(pos + 1));
            }
            return isHex(hex.toString()) ? hex.toString().toUpperCase(Locale.ROOT) : null;
        }
        return null;
    }

    private static boolean isHex(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
