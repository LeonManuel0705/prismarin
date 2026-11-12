package net.prismarin.shared.chatfilter;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TextNormalizer {

    private static final Map<Character, Character> LEET = Map.ofEntries(
            Map.entry('0', 'o'), Map.entry('1', 'i'), Map.entry('3', 'e'), Map.entry('4', 'a'),
            Map.entry('5', 's'), Map.entry('7', 't'), Map.entry('8', 'b'), Map.entry('9', 'g'),
            Map.entry('@', 'a'), Map.entry('$', 's'), Map.entry('!', 'i'), Map.entry('|', 'i'),
            Map.entry('\u20AC', 'e'), Map.entry('\u00A3', 'l'));

    private static final Map<Character, Character> LOOKALIKES = Map.ofEntries(
            Map.entry('\u0430', 'a'), Map.entry('\u0432', 'b'), Map.entry('\u0435', 'e'), Map.entry('\u0451', 'e'),
            Map.entry('\u043A', 'k'), Map.entry('\u043C', 'm'), Map.entry('\u043D', 'h'), Map.entry('\u043E', 'o'),
            Map.entry('\u0440', 'p'), Map.entry('\u0441', 'c'), Map.entry('\u0442', 't'), Map.entry('\u0443', 'y'),
            Map.entry('\u0445', 'x'), Map.entry('\u0456', 'i'), Map.entry('\u0458', 'j'), Map.entry('\u0455', 's'),
            Map.entry('\u03B1', 'a'), Map.entry('\u03BF', 'o'), Map.entry('\u03C1', 'p'), Map.entry('\u03B9', 'i'),
            Map.entry('\u03BA', 'k'), Map.entry('\u03BD', 'v'), Map.entry('\u03C4', 't'), Map.entry('\u03C5', 'u'),
            Map.entry('\u0131', 'i'));

    private static final int MAX_RUN = 2;

    private TextNormalizer() {
        throw new AssertionError();
    }

    public static String fold(String text) {
        String lower = text.toLowerCase(Locale.ROOT).replace("\u00DF", "ss");
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFKD);
        StringBuilder out = new StringBuilder(decomposed.length());
        for (int index = 0; index < decomposed.length(); index++) {
            char c = decomposed.charAt(index);
            int type = Character.getType(c);
            if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                    || type == Character.COMBINING_SPACING_MARK) {
                continue;
            }
            out.append(LOOKALIKES.getOrDefault(c, c));
        }
        return out.toString();
    }

    public static String canonical(String token) {
        String folded = fold(token);
        StringBuilder out = new StringBuilder(folded.length());
        for (int index = 0; index < folded.length(); index++) {
            char c = folded.charAt(index);
            if (Character.isLetter(c)) {
                out.append(c);
            } else {
                Character mapped = LEET.get(c);
                if (mapped != null) {
                    out.append(mapped.charValue());
                }
            }
        }
        return out.toString();
    }

    public static boolean leet(char c) {
        return LEET.containsKey(c);
    }

    public static int[] trimmed(String text, int start, int end) {
        int from = start;
        int to = end;
        while (from < to && !Character.isLetterOrDigit(text.charAt(from))) {
            from++;
        }
        while (to > from && !Character.isLetterOrDigit(text.charAt(to - 1))) {
            to--;
        }
        return new int[]{from, to};
    }

    public static String simplify(String text) {
        String folded = fold(text);
        StringBuilder out = new StringBuilder(folded.length());
        boolean space = false;
        char last = 0;
        int run = 0;
        for (int index = 0; index < folded.length(); index++) {
            char c = folded.charAt(index);
            if (Character.isLetterOrDigit(c)) {
                if (space && !out.isEmpty()) {
                    out.append(' ');
                    last = ' ';
                    run = 0;
                }
                space = false;
                run = c == last ? run + 1 : 1;
                last = c;
                if (run <= MAX_RUN) {
                    out.append(c);
                }
            } else if (Character.isWhitespace(c)) {
                space = true;
            }
        }
        return out.toString();
    }

    public static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        for (String part : text.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                words.add(part);
            }
        }
        return words;
    }
}
