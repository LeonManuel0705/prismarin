package net.prismarin.api.social;

import java.util.regex.Pattern;

public final class ClanData {

    public static final int MAX_KEYS = 32;
    public static final int MAX_VALUE_LENGTH = 256;

    private static final Pattern KEY = Pattern.compile("[a-z0-9_.:-]{1,48}");

    private ClanData() {
        throw new AssertionError();
    }

    public static boolean validKey(String key) {
        return key != null && KEY.matcher(key).matches();
    }

    public static boolean validValue(String value) {
        return value == null || value.length() <= MAX_VALUE_LENGTH;
    }
}
