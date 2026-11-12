package net.prismarin.api.cosmetics;

import java.util.Locale;
import java.util.regex.Pattern;

public record CosmeticPrice(String currency, long amount) {

    private static final Pattern CURRENCY = Pattern.compile("[a-z0-9_-]{1,32}");

    public CosmeticPrice {
        currency = currency == null ? "" : currency.strip().toLowerCase(Locale.ROOT);
        if (!CURRENCY.matcher(currency).matches()) {
            throw new IllegalArgumentException("'" + currency + "' is no currency id");
        }
        if (amount <= 0L) {
            throw new IllegalArgumentException("a price must be above 0");
        }
    }

    public static CosmeticPrice of(String currency, long amount) {
        return new CosmeticPrice(currency, amount);
    }
}
