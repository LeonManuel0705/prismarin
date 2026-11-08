package net.prismarin.shared.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import org.junit.jupiter.api.Test;

class DisabledEconomyServiceTest {

    private final DisabledEconomyService service = new DisabledEconomyService();
    private final UUID uuid = UUID.randomUUID();

    @Test
    void reportsDisabledWithoutCurrencies() {
        assertFalse(service.enabled());
        assertTrue(service.currencies().isEmpty());
        assertTrue(service.currency("coins").isEmpty());
        assertThrows(IllegalStateException.class, service::primaryCurrency);
    }

    @Test
    void transactionsFailWithSystemDisabled() {
        assertEquals(EconomyResult.FailureReason.SYSTEM_DISABLED,
                service.deposit(uuid, Currency.COINS, 5L).join().failureReason());
        assertEquals(EconomyResult.FailureReason.SYSTEM_DISABLED,
                service.withdraw(uuid, Currency.COINS, 5L).join().failureReason());
        assertEquals(EconomyResult.FailureReason.SYSTEM_DISABLED,
                service.set(uuid, Currency.COINS, 5L).join().failureReason());
        assertEquals(0L, service.balance(uuid, Currency.COINS).join());
    }
}
