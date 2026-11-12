package net.prismarin.velocity.social;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public record ClanNaming(int nameMin, int nameMax, int tagMin, int tagMax, Pattern allowed, Set<String> blocked) {

    public static final String DEFAULT_PATTERN = "[A-Za-z0-9_]+";
    public static final ClanNaming DEFAULT = new ClanNaming(3, 16, 2, 5, Pattern.compile(DEFAULT_PATTERN),
            Set.of("admin", "staff", "team", "mod", "owner", "server"));

    public enum Check {
        OK,
        TOO_SHORT,
        TOO_LONG,
        CHARACTERS,
        BLOCKED
    }

    public ClanNaming {
        allowed = allowed == null ? Pattern.compile(DEFAULT_PATTERN) : allowed;
        blocked = Set.copyOf(blocked.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList());
    }

    public Check name(String input) {
        return check(input, nameMin, nameMax);
    }

    public Check tag(String input) {
        return check(input, tagMin, tagMax);
    }

    private Check check(String input, int min, int max) {
        String value = input == null ? "" : input.strip();
        int length = value.codePointCount(0, value.length());
        if (length < min) {
            return Check.TOO_SHORT;
        }
        if (length > max) {
            return Check.TOO_LONG;
        }
        if (!allowed.matcher(value).matches()) {
            return Check.CHARACTERS;
        }
        if (blocked.contains(value.toLowerCase(Locale.ROOT))) {
            return Check.BLOCKED;
        }
        return Check.OK;
    }
}
