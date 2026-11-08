package net.prismarin.core.bridge;

public final class VaultAmounts {

    private VaultAmounts() {
        throw new AssertionError();
    }

    public static long toUnits(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0) {
            return -1L;
        }
        if (amount >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.round(amount);
    }
}
