package net.prismarin.api.economy;

public record EconomyResult(boolean success, long newBalance, FailureReason failureReason) {

    public enum FailureReason {
        NONE,
        INSUFFICIENT_FUNDS,
        PROFILE_NOT_LOADED,
        UNKNOWN_CURRENCY,
        SYSTEM_DISABLED
    }

    public static EconomyResult success(long newBalance) {
        return new EconomyResult(true, newBalance, FailureReason.NONE);
    }

    public static EconomyResult failure(FailureReason reason, long balance) {
        return new EconomyResult(false, balance, reason);
    }
}
