package net.prismarin.shared.economy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import net.prismarin.api.economy.EconomyService;

public final class DisabledEconomyService implements EconomyService {

    private static final CompletableFuture<EconomyResult> DISABLED = CompletableFuture.completedFuture(
            EconomyResult.failure(EconomyResult.FailureReason.SYSTEM_DISABLED, 0L));

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public List<Currency> currencies() {
        return List.of();
    }

    @Override
    public Currency primaryCurrency() {
        throw new IllegalStateException("the economy system is disabled in config.yml");
    }

    @Override
    public CompletableFuture<Long> balance(UUID uuid, Currency currency) {
        return CompletableFuture.completedFuture(0L);
    }

    @Override
    public CompletableFuture<EconomyResult> deposit(UUID uuid, Currency currency, long amount) {
        return DISABLED;
    }

    @Override
    public CompletableFuture<EconomyResult> withdraw(UUID uuid, Currency currency, long amount) {
        return DISABLED;
    }

    @Override
    public CompletableFuture<EconomyResult> set(UUID uuid, Currency currency, long value) {
        return DISABLED;
    }
}
