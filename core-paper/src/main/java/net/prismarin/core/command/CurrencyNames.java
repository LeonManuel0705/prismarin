package net.prismarin.core.command;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.locale.LocaleService;

public final class CurrencyNames {

    private CurrencyNames() {
        throw new AssertionError();
    }

    public static Component render(LocaleService locale, Currency currency, Locale target) {
        String key = currency.messageKey();
        if (locale.raw(key, target).equals(key)) {
            return Component.text(Character.toUpperCase(currency.id().charAt(0)) + currency.id().substring(1));
        }
        return locale.render(key, target);
    }
}
