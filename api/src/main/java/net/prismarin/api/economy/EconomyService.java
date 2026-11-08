package net.prismarin.api.economy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface EconomyService {

    default boolean enabled() {
        return true;
    }

    List<Currency> currencies();

    Currency primaryCurrency();

    default Optional<Currency> currency(String id) {
        return currencies().stream().filter(currency -> currency.id().equalsIgnoreCase(id)).findFirst();
    }

    CompletableFuture<Long> balance(UUID uuid, Currency currency);

    CompletableFuture<EconomyResult> deposit(UUID uuid, Currency currency, long amount);

    CompletableFuture<EconomyResult> withdraw(UUID uuid, Currency currency, long amount);

    CompletableFuture<EconomyResult> set(UUID uuid, Currency currency, long value);
}
