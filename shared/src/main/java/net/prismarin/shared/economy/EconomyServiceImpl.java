package net.prismarin.shared.economy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import net.prismarin.api.economy.BalanceChangedEvent;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.event.EventBus;
import net.prismarin.shared.storage.ProfileRepository;

public final class EconomyServiceImpl implements EconomyService {

    private final ProfileRepository repository;
    private final ExecutorService executor;
    private final List<Currency> currencies;
    private final Currency primaryCurrency;
    private final EventBus events;

    public EconomyServiceImpl(ProfileRepository repository, ExecutorService executor, List<Currency> currencies,
                              Currency primaryCurrency, EventBus events) {
        this.repository = repository;
        this.executor = executor;
        this.currencies = List.copyOf(currencies);
        this.primaryCurrency = primaryCurrency;
        this.events = events;
        if (!this.currencies.contains(primaryCurrency)) {
            throw new IllegalArgumentException("primary currency " + primaryCurrency.id() + " is not declared");
        }
    }

    @Override
    public List<Currency> currencies() {
        return currencies;
    }

    @Override
    public Currency primaryCurrency() {
        return primaryCurrency;
    }

    @Override
    public CompletableFuture<Long> balance(UUID uuid, Currency currency) {
        if (!currencies.contains(currency)) {
            return CompletableFuture.completedFuture(0L);
        }
        return CompletableFuture.supplyAsync(
                () -> repository.find(uuid).map(profile -> profile.balance(currency)).orElse(0L),
                executor
        );
    }

    @Override
    public CompletableFuture<EconomyResult> deposit(UUID uuid, Currency currency, long amount) {
        requirePositive(amount);
        return transact(uuid, currency, BalanceChangedEvent.Type.DEPOSIT, amount,
                () -> repository.incrementCurrency(uuid, currency, amount, true));
    }

    @Override
    public CompletableFuture<EconomyResult> withdraw(UUID uuid, Currency currency, long amount) {
        requirePositive(amount);
        return transact(uuid, currency, BalanceChangedEvent.Type.WITHDRAW, amount,
                () -> repository.incrementCurrency(uuid, currency, -amount, false));
    }

    @Override
    public CompletableFuture<EconomyResult> set(UUID uuid, Currency currency, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must not be negative");
        }
        return transact(uuid, currency, BalanceChangedEvent.Type.SET, value,
                () -> repository.setCurrency(uuid, currency, value));
    }

    private CompletableFuture<EconomyResult> transact(UUID uuid, Currency currency, BalanceChangedEvent.Type type,
                                                      long amount, Transaction transaction) {
        if (!currencies.contains(currency)) {
            return CompletableFuture.completedFuture(
                    EconomyResult.failure(EconomyResult.FailureReason.UNKNOWN_CURRENCY, 0L));
        }
        return CompletableFuture.supplyAsync(() -> {
            EconomyResult result = transaction.run();
            if (result.success()) {
                events.publish(new BalanceChangedEvent(uuid, currency, type, amount, result.newBalance()));
            }
            return result;
        }, executor);
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be strictly positive");
        }
    }

    @FunctionalInterface
    private interface Transaction {
        EconomyResult run();
    }
}
