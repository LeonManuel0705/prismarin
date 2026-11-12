package net.prismarin.shared.chatfilter;

import java.util.Locale;
import java.util.OptionalInt;

public final class CapsCheck {

    private CapsCheck() {
        throw new AssertionError();
    }

    public static OptionalInt percent(String text, int minLetters) {
        int letters = 0;
        int upper = 0;
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    upper++;
                }
            }
        }
        if (letters == 0 || letters < minLetters) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(upper * 100 / letters);
    }

    public static String soften(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (int index = 0; index < lower.length(); index++) {
            if (Character.isLetter(lower.charAt(index))) {
                return lower.substring(0, index) + Character.toUpperCase(lower.charAt(index))
                        + lower.substring(index + 1);
            }
        }
        return lower;
    }
}
