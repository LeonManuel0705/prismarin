package net.prismarin.shared.economy;

import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.shared.storage.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EconomyServiceImplTest {

    @Mock
    private ProfileRepository repository;

    private EconomyServiceImpl service;
    private final UUID uuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EconomyServiceImpl(repository);
    }

    @Test
    void depositDelegatesToAtomicIncrementAllowingNegative() {
        when(repository.incrementCurrency(uuid, Currency.COINS, 100L, true))
                .thenReturn(EconomyResult.success(100L));

        EconomyResult result = service.deposit(uuid, Currency.COINS, 100L).join();

        assertTrue(result.success());
        assertEquals(100L, result.newBalance());
        assertEquals(EconomyResult.FailureReason.NONE, result.failureReason());
        verify(repository).incrementCurrency(uuid, Currency.COINS, 100L, true);
    }

    @Test
    void withdrawMapsInsufficientFunds() {
        when(repository.incrementCurrency(uuid, Currency.GEMS, -50L, false))
                .thenReturn(EconomyResult.failure(EconomyResult.FailureReason.INSUFFICIENT_FUNDS, 10L));

        EconomyResult result = service.withdraw(uuid, Currency.GEMS, 50L).join();

        assertFalse(result.success());
        assertEquals(10L, result.newBalance());
        assertEquals(EconomyResult.FailureReason.INSUFFICIENT_FUNDS, result.failureReason());
    }

    @Test
    void withdrawMapsProfileNotLoaded() {
        when(repository.incrementCurrency(uuid, Currency.COINS, -5L, false))
                .thenReturn(EconomyResult.failure(EconomyResult.FailureReason.PROFILE_NOT_LOADED, 0L));

        EconomyResult result = service.withdraw(uuid, Currency.COINS, 5L).join();

        assertFalse(result.success());
        assertEquals(EconomyResult.FailureReason.PROFILE_NOT_LOADED, result.failureReason());
    }

    @Test
    void negativeAndZeroAmountsAreRejectedBeforeTouchingStorage() {
        assertThrows(IllegalArgumentException.class, () -> service.deposit(uuid, Currency.COINS, -1L));
        assertThrows(IllegalArgumentException.class, () -> service.deposit(uuid, Currency.COINS, 0L));
        assertThrows(IllegalArgumentException.class, () -> service.withdraw(uuid, Currency.GEMS, -20L));
        verifyNoInteractions(repository);
    }

    @Test
    void balanceReadsFromRepository() {
        PlayerProfile profile = PlayerProfile.create(uuid, "Leon", "de");
        profile.setCoins(42L);
        when(repository.find(uuid)).thenReturn(Optional.of(profile));

        assertEquals(42L, service.balance(uuid, Currency.COINS).join());
    }

    @Test
    void balanceOfUnknownProfileIsZero() {
        when(repository.find(uuid)).thenReturn(Optional.empty());

        assertEquals(0L, service.balance(uuid, Currency.GEMS).join());
    }
}
