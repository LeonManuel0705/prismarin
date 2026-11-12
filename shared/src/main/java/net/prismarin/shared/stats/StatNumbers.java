package net.prismarin.shared.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.prismarin.api.stats.StatFormat;

public final class StatNumbers {

    public enum Decimal {
        AUTO("auto"),
        DOT("."),
        COMMA(",");

        private final String id;

        Decimal(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Optional<Decimal> byId(String id) {
            if (id == null) {
                return Optional.empty();
            }
            return switch (id.strip().toLowerCase(Locale.ROOT)) {
                case "auto", "" -> Optional.of(AUTO);
                case ".", "dot", "point" -> Optional.of(DOT);
                case ",", "comma" -> Optional.of(COMMA);
                default -> Optional.empty();
            };
        }
    }

    private static final Pattern CODES = Pattern.compile(
            "(?i)[§&]x(?:[§&][0-9a-f]){6}|&#[0-9a-f]{6}|[§&][0-9a-fk-or]|<[^<>]*>");
    private static final Pattern GAPS = Pattern.compile("(?<=\\d)[\\s  '’_]+(?=\\d)");
    private static final Pattern CLOCK = Pattern.compile(
            "([-+]?)(\\d+):(\\d{1,2})(?::(\\d{1,2}))?(?:[.,](\\d{1,3}))?");
    private static final Pattern UNITS = Pattern.compile(
            "(?i)(?:(\\d+)\\s*d(?:ays?)?)?\\s*(?:(\\d+)\\s*h(?:ours?)?)?\\s*(?:(\\d+)\\s*m(?:in(?:utes?)?)?)?"
                    + "\\s*(?:(\\d+)\\s*s(?:ec(?:onds?)?)?)?");
    private static final Pattern NUMBER = Pattern.compile("([-+]?)(\\d[\\d.,]*+)(?:([kKmMbB])(?!\\p{L}))?");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100L);

    private StatNumbers() {
        throw new AssertionError();
    }

    public static OptionalLong parse(String text, StatFormat format, Decimal decimal) {
        if (text == null || text.isBlank()) {
            return OptionalLong.empty();
        }
        StatFormat kind = format == null ? StatFormat.NUMBER : format;
        Decimal separator = decimal == null ? Decimal.AUTO : decimal;
        String clean = GAPS.matcher(CODES.matcher(text).replaceAll("")).replaceAll("").strip();
        if (clean.isEmpty()) {
            return OptionalLong.empty();
        }
        boolean time = kind == StatFormat.DURATION || kind == StatFormat.MILLIS;
        Matcher clock = CLOCK.matcher(clean);
        if (time ? clock.find() : clock.matches()) {
            return clock(clock, kind == StatFormat.MILLIS);
        }
        if (time) {
            OptionalLong units = units(clean, kind == StatFormat.MILLIS);
            if (units.isPresent()) {
                return units;
            }
        }
        Matcher number = NUMBER.matcher(clean);
        if (!number.find()) {
            return OptionalLong.empty();
        }
        Optional<BigDecimal> value = value(number.group(2), separator, kind);
        if (value.isEmpty()) {
            return OptionalLong.empty();
        }
        BigDecimal result = value.get();
        if (!time && number.group(3) != null) {
            result = result.multiply(suffix(number.group(3)));
        }
        if (kind == StatFormat.DECIMAL) {
            result = result.multiply(HUNDRED);
        }
        if ("-".equals(number.group(1))) {
            result = result.negate();
        }
        try {
            return OptionalLong.of(result.setScale(0, RoundingMode.HALF_UP).longValueExact());
        } catch (ArithmeticException e) {
            return OptionalLong.empty();
        }
    }

    static char decimalSeparator(String digits, Decimal decimal, StatFormat format) {
        if (decimal == Decimal.DOT) {
            return '.';
        }
        if (decimal == Decimal.COMMA) {
            return ',';
        }
        int dots = count(digits, '.');
        int commas = count(digits, ',');
        if (dots > 0 && commas > 0) {
            char last = digits.lastIndexOf('.') > digits.lastIndexOf(',') ? '.' : ',';
            return count(digits, last) == 1 ? last : 0;
        }
        if (dots == 0 && commas == 0) {
            return 0;
        }
        char only = dots > 0 ? '.' : ',';
        if (count(digits, only) > 1) {
            return 0;
        }
        int after = digits.length() - digits.indexOf(only) - 1;
        return after == 3 && format != StatFormat.DECIMAL ? 0 : only;
    }

    private static Optional<BigDecimal> value(String raw, Decimal decimal, StatFormat format) {
        String digits = raw.replaceAll("[.,]+$", "");
        if (digits.isEmpty()) {
            return Optional.empty();
        }
        char separator = decimalSeparator(digits, decimal, format);
        StringBuilder plain = new StringBuilder(digits.length());
        for (int index = 0; index < digits.length(); index++) {
            char character = digits.charAt(index);
            if (Character.isDigit(character)) {
                plain.append(character);
            } else if (character == separator) {
                plain.append('.');
            }
        }
        try {
            return Optional.of(new BigDecimal(plain.toString()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static OptionalLong clock(Matcher clock, boolean millis) {
        try {
            long first = Long.parseLong(clock.group(2));
            long second = Long.parseLong(clock.group(3));
            long seconds = clock.group(4) == null ? first * 60L + second
                    : first * 3_600L + second * 60L + Long.parseLong(clock.group(4));
            long fraction = clock.group(5) == null ? 0L
                    : Long.parseLong((clock.group(5) + "00").substring(0, 3));
            long value = millis ? Math.addExact(Math.multiplyExact(seconds, 1_000L), fraction) : seconds;
            return OptionalLong.of("-".equals(clock.group(1)) ? -value : value);
        } catch (ArithmeticException | NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private static OptionalLong units(String text, boolean millis) {
        Matcher units = UNITS.matcher(text);
        while (units.find()) {
            if (units.group(1) == null && units.group(2) == null && units.group(3) == null
                    && units.group(4) == null) {
                continue;
            }
            try {
                long seconds = Math.addExact(Math.addExact(Math.multiplyExact(number(units.group(1)), 86_400L),
                        Math.multiplyExact(number(units.group(2)), 3_600L)),
                        Math.addExact(Math.multiplyExact(number(units.group(3)), 60L), number(units.group(4))));
                return OptionalLong.of(millis ? Math.multiplyExact(seconds, 1_000L) : seconds);
            } catch (ArithmeticException | NumberFormatException e) {
                return OptionalLong.empty();
            }
        }
        return OptionalLong.empty();
    }

    private static long number(String group) {
        return group == null ? 0L : Long.parseLong(group);
    }

    private static BigDecimal suffix(String suffix) {
        return switch (suffix.toLowerCase(Locale.ROOT)) {
            case "k" -> BigDecimal.valueOf(1_000L);
            case "m" -> BigDecimal.valueOf(1_000_000L);
            default -> BigDecimal.valueOf(1_000_000_000L);
        };
    }

    private static int count(String text, char character) {
        int found = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == character) {
                found++;
            }
        }
        return found;
    }
}
