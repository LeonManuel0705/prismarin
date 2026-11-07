package net.prismarin.api.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CurrencyTest {

    @Test
    void idIsNormalizedToLowercase() {
        assertEquals(Currency.COINS, Currency.of("Coins"));
    }

    @Test
    void messageKeyUsesId() {
        assertEquals("currency.tokens", Currency.of("tokens").messageKey());
    }

    @Test
    void invalidIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Currency.of(""));
        assertThrows(IllegalArgumentException.class, () -> Currency.of("two words"));
        assertThrows(IllegalArgumentException.class, () -> Currency.of("a".repeat(33)));
    }
}
