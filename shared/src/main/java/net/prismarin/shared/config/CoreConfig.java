package net.prismarin.shared.config;

import java.util.List;
import java.util.Map;
import net.prismarin.api.brand.Branding;
import net.prismarin.api.economy.Currency;

public record CoreConfig(String serverId, StorageMode storageMode, String mongoUri, String mongoDatabase,
                         boolean redisEnabled, String redisHost, int redisPort, String redisPassword,
                         String defaultLocale, Branding branding, List<Currency> currencies,
                         Currency primaryCurrency, Map<String, Boolean> modules) {

    public CoreConfig {
        currencies = List.copyOf(currencies);
        modules = Map.copyOf(modules);
        if (currencies.isEmpty()) {
            throw new IllegalArgumentException("economy.currencies must declare at least one currency");
        }
        if (!currencies.contains(primaryCurrency)) {
            throw new IllegalArgumentException("economy.primary '" + primaryCurrency.id()
                    + "' is not listed in economy.currencies");
        }
    }

    public boolean moduleEnabled(String module) {
        return modules.getOrDefault(module, true);
    }

    public enum StorageMode {
        LOCAL,
        MONGO
    }
}
