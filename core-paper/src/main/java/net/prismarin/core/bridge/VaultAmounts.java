package net.prismarin.core.bridge;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class VaultAmounts {

    private static final BigDecimal MAX_UNITS = BigDecimal.valueOf(Long.MAX_VALUE);

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

    public static long toUnits(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            return -1L;
        }
        if (amount.compareTo(MAX_UNITS) >= 0) {
            return Long.MAX_VALUE;
        }
        return amount.setScale(0, RoundingMode.HALF_UP).longValue();
    }
}
