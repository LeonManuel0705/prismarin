package net.prismarin.example;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.Prismarin;
import net.prismarin.api.PrismarinSystems;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;

final class Rewards {

    static final long XP_PER_REWARD = 50L;

    private final Prismarin prismarin;

    Rewards(Prismarin prismarin) {
        this.prismarin = prismarin;
    }

    CompletableFuture<EconomyResult> pay(UUID player, long amount) {
        if (!prismarin.systemEnabled(PrismarinSystems.ECONOMY)) {
            return CompletableFuture.completedFuture(EconomyResult.failure(
                    EconomyResult.FailureReason.SYSTEM_DISABLED, 0L));
        }
        Currency coins = prismarin.economy().primaryCurrency();
        return prismarin.economy().deposit(player, coins, amount)
                .thenCompose(result -> addXp(player).thenApply(done -> result));
    }

    Currency currency() {
        return prismarin.economy().primaryCurrency();
    }

    private CompletableFuture<Void> addXp(UUID player) {
        if (!prismarin.systemEnabled(PrismarinSystems.LEVELS)) {
            return CompletableFuture.completedFuture(null);
        }
        return prismarin.profiles().modify(player, profile -> profile.addXp(XP_PER_REWARD)).thenApply(profile -> null);
    }
}
