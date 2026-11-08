package net.prismarin.core.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VaultAmountsTest {

    @Test
    void roundsToWholeUnits() {
        assertEquals(10L, VaultAmounts.toUnits(10D));
        assertEquals(3L, VaultAmounts.toUnits(2.6D));
        assertEquals(0L, VaultAmounts.toUnits(0.4D));
    }

    @Test
    void rejectsInvalidAmounts() {
        assertEquals(-1L, VaultAmounts.toUnits(-5D));
        assertEquals(-1L, VaultAmounts.toUnits(Double.NaN));
        assertEquals(-1L, VaultAmounts.toUnits(Double.POSITIVE_INFINITY));
        assertEquals(Long.MAX_VALUE, VaultAmounts.toUnits(1e30));
    }

    @Test
    void bigDecimalAmountsRoundHalfUp() {
        assertEquals(3L, VaultAmounts.toUnits(new java.math.BigDecimal("2.5")));
        assertEquals(2L, VaultAmounts.toUnits(new java.math.BigDecimal("2.49")));
        assertEquals(-1L, VaultAmounts.toUnits(new java.math.BigDecimal("-1")));
        assertEquals(-1L, VaultAmounts.toUnits((java.math.BigDecimal) null));
        assertEquals(Long.MAX_VALUE, VaultAmounts.toUnits(new java.math.BigDecimal("1e40")));
    }
}
