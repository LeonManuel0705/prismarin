package net.prismarin.api.economy;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record Currency(String id) {

    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");

    public static final Currency COINS = new Currency("coins");

    public static final Currency GEMS = new Currency("gems");

    public Currency {
        Objects.requireNonNull(id, "id");
        id = id.toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("invalid currency id: " + id);
        }
    }

    public static Currency of(String id) {
        return new Currency(id);
    }

    public String messageKey() {
        return "currency." + id;
    }
}
