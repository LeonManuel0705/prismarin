package net.prismarin.api.economy;

/**
 * Outcome of an economy transaction.
 *
 * @param success whether the transaction was applied
 * @param newBalance the balance after the transaction, or the unchanged
 *        balance if the transaction failed
 * @param failureReason the reason for failure, {@link FailureReason#NONE}
 *        on success
 */
public record EconomyResult(boolean success, long newBalance, FailureReason failureReason) {

    /**
     * Reasons an economy transaction can fail.
     */
    public enum FailureReason {

        /**
         * The transaction succeeded.
         */
        NONE,

        /**
         * The withdrawal would have driven the balance below zero.
         */
        INSUFFICIENT_FUNDS,

        /**
         * No profile exists in storage for the target player.
         */
        PROFILE_NOT_LOADED
    }

    /**
     * Creates a successful result.
     *
     * @param newBalance the balance after the transaction
     * @return a successful result carrying the new balance
     */
    public static EconomyResult success(long newBalance) {
        return new EconomyResult(true, newBalance, FailureReason.NONE);
    }

    /**
     * Creates a failed result.
     *
     * @param reason the failure reason
     * @param balance the unchanged balance at the time of failure
     * @return a failed result carrying the reason and current balance
     */
    public static EconomyResult failure(FailureReason reason, long balance) {
        return new EconomyResult(false, balance, reason);
    }
}
