package net.prismarin.shared.economy;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.shared.storage.ProfileRepository;

public final class EconomyServiceImpl implements EconomyService {

    private final ProfileRepository repository;
    private final ExecutorService executor;

    public EconomyServiceImpl(ProfileRepository repository) {
        this(repository, Executors.newVirtualThreadPerTaskExecutor());
    }

    public EconomyServiceImpl(ProfileRepository repository, ExecutorService executor) {
        this.repository = repository;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Long> balance(UUID uuid, Currency currency) {
        return CompletableFuture.supplyAsync(
                () -> repository.find(uuid).map(profile -> profile.balance(currency)).orElse(0L),
                executor
        );
    }

    @Override
    public CompletableFuture<EconomyResult> deposit(UUID uuid, Currency currency, long amount) {
        requirePositive(amount);
        return CompletableFuture.supplyAsync(
                () -> repository.incrementCurrency(uuid, currency, amount, true),
                executor
        );
    }

    @Override
    public CompletableFuture<EconomyResult> withdraw(UUID uuid, Currency currency, long amount) {
        requirePositive(amount);
        return CompletableFuture.supplyAsync(
                () -> repository.incrementCurrency(uuid, currency, -amount, false),
                executor
        );
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be strictly positive");
        }
    }
}
