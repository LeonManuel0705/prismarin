package net.prismarin.api.economy;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous access to player balances with atomic transaction
 * semantics backed by the storage layer.
 */
public interface EconomyService {

    /**
     * Reads the current balance for the given currency.
     *
     * @param uuid the player's unique id
     * @param currency the currency to read
     * @return a future completing with the balance, 0 if no profile exists
     */
    CompletableFuture<Long> balance(UUID uuid, Currency currency);

    /**
     * Atomically adds the given amount to the player's balance.
     *
     * @param uuid the player's unique id
     * @param currency the currency to credit
     * @param amount the strictly positive amount to add
     * @return a future completing with the transaction result
     * @throws IllegalArgumentException if the amount is not strictly positive
     */
    CompletableFuture<EconomyResult> deposit(UUID uuid, Currency currency, long amount);

    /**
     * Atomically removes the given amount from the player's balance,
     * failing with {@link EconomyResult.FailureReason#INSUFFICIENT_FUNDS}
     * instead of allowing a negative balance.
     *
     * @param uuid the player's unique id
     * @param currency the currency to debit
     * @param amount the strictly positive amount to remove
     * @return a future completing with the transaction result
     * @throws IllegalArgumentException if the amount is not strictly positive
     */
    CompletableFuture<EconomyResult> withdraw(UUID uuid, Currency currency, long amount);
}
