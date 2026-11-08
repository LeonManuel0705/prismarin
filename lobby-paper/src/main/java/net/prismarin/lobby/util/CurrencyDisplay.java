package net.prismarin.lobby.util;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.core.command.CurrencyNames;

@Singleton
public final class CurrencyDisplay {

    private static final int MAX_SHOWN = 3;

    private final EconomyService economy;
    private final LocaleService locale;

    @Inject
    public CurrencyDisplay(EconomyService economy, LocaleService locale) {
        this.economy = economy;
        this.locale = locale;
    }

    public List<Currency> shown() {
        return economy.enabled() ? economy.currencies().stream().limit(MAX_SHOWN).toList() : List.of();
    }

    public Component render(String keyPrefix, Currency currency, long amount, Locale target) {
        return render(keyPrefix, currency, amount, target, null);
    }

    public Component render(String keyPrefix, Currency currency, long amount, Locale target, String suffix) {
        String tail = suffix == null ? "" : "." + suffix;
        String key = keyPrefix + "." + currency.id() + tail;
        if (locale.raw(key, target).equals(key)) {
            key = keyPrefix + ".currency" + tail;
        }
        String value = String.valueOf(amount);
        return locale.render(key, target,
                Placeholder.unparsed("amount", value),
                Placeholder.unparsed(currency.id(), value),
                Placeholder.component("currency", CurrencyNames.render(locale, currency, target)));
    }
}
