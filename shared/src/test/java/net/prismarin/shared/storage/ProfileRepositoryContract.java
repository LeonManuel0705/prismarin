package net.prismarin.shared.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import net.prismarin.api.profile.PlayerProfile;
import org.junit.jupiter.api.Test;

abstract class ProfileRepositoryContract {

    protected abstract ProfileRepository repository();

    private static PlayerProfile sampleProfile(UUID uuid) {
        Map<String, String> settings = new HashMap<>();
        settings.put("scoreboard", "true");
        return new PlayerProfile(uuid, "Leon", Instant.ofEpochMilli(1000L), Instant.ofEpochMilli(2000L),
                "de", Map.of(Currency.COINS, 100L, Currency.GEMS, 5L), 3, 200L, settings);
    }

    @Test
    void roundtripPreservesAllFields() {
        UUID uuid = UUID.randomUUID();
        repository().save(sampleProfile(uuid));

        Optional<PlayerProfile> found = repository().find(uuid);

        assertTrue(found.isPresent());
        PlayerProfile profile = found.get();
        assertEquals(uuid, profile.getUuid());
        assertEquals("Leon", profile.getName());
        assertEquals(Instant.ofEpochMilli(1000L), profile.getFirstJoin());
        assertEquals(Instant.ofEpochMilli(2000L), profile.getLastSeen());
        assertEquals("de", profile.getLocale());
        assertEquals(100L, profile.balance(Currency.COINS));
        assertEquals(5L, profile.balance(Currency.GEMS));
        assertEquals(3, profile.getLevel());
        assertEquals(200L, profile.getXp());
        assertEquals(Map.of("scoreboard", "true"), Map.copyOf(profile.getSettings()));
    }

    @Test
    void findOfUnknownUuidIsEmpty() {
        assertTrue(repository().find(UUID.randomUUID()).isEmpty());
    }

    @Test
    void saveUpdatesProfileFieldsButNeverStoredBalances() {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = sampleProfile(uuid);
        repository().save(profile);
        repository().incrementCurrency(uuid, Currency.COINS, 400L, true);

        profile.balance(Currency.COINS, 999L);
        profile.balance(Currency.of("tokens"), 50L);
        profile.setName("LeonNeu");
        profile.setLastSeen(Instant.ofEpochMilli(9000L));
        profile.setLevel(4);
        profile.getSettings().put("chat", "off");
        repository().save(profile);

        PlayerProfile stored = repository().find(uuid).orElseThrow();
        assertEquals("LeonNeu", stored.getName());
        assertEquals(Instant.ofEpochMilli(9000L), stored.getLastSeen());
        assertEquals(4, stored.getLevel());
        assertEquals("off", stored.getSettings().get("chat"));
        assertEquals(500L, stored.balance(Currency.COINS));
        assertEquals(5L, stored.balance(Currency.GEMS));
        assertEquals(0L, stored.balance(Currency.of("tokens")));
    }

    @Test
    void incrementCurrencyAppliesDelta() {
        UUID uuid = UUID.randomUUID();
        repository().save(sampleProfile(uuid));

        EconomyResult result = repository().incrementCurrency(uuid, Currency.COINS, 50L, true);

        assertTrue(result.success());
        assertEquals(150L, result.newBalance());
        assertEquals(150L, repository().find(uuid).orElseThrow().balance(Currency.COINS));
    }

    @Test
    void incrementCurrencyGuardsAgainstNegativeBalance() {
        UUID uuid = UUID.randomUUID();
        repository().save(sampleProfile(uuid));

        EconomyResult result = repository().incrementCurrency(uuid, Currency.COINS, -200L, false);

        assertFalse(result.success());
        assertEquals(EconomyResult.FailureReason.INSUFFICIENT_FUNDS, result.failureReason());
        assertEquals(100L, result.newBalance());
        assertEquals(100L, repository().find(uuid).orElseThrow().balance(Currency.COINS));
    }

    @Test
    void incrementCurrencyCanWithdrawEverything() {
        UUID uuid = UUID.randomUUID();
        repository().save(sampleProfile(uuid));

        EconomyResult result = repository().incrementCurrency(uuid, Currency.COINS, -100L, false);

        assertTrue(result.success());
        assertEquals(0L, result.newBalance());
        assertEquals(0L, repository().find(uuid).orElseThrow().balance(Currency.COINS));
    }

    @Test
    void incrementCurrencyAllowsNegativeWhenPermitted() {
        UUID uuid = UUID.randomUUID();
        repository().save(sampleProfile(uuid));

        EconomyResult result = repository().incrementCurrency(uuid, Currency.COINS, -200L, true);

        assertTrue(result.success());
        assertEquals(-100L, result.newBalance());
        assertEquals(-100L, repository().find(uuid).orElseThrow().balance(Currency.COINS));
    }

    @Test
    void incrementCurrencySaturatesInsteadOfOverflowing() {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = sampleProfile(uuid);
        profile.balance(Currency.COINS, Long.MAX_VALUE - 10L);
        repository().save(profile);

        EconomyResult result = repository().incrementCurrency(uuid, Currency.COINS, 100L, true);

        assertTrue(result.success());
        assertEquals(Long.MAX_VALUE, result.newBalance());
        assertEquals(Long.MAX_VALUE, repository().find(uuid).orElseThrow().balance(Currency.COINS));
    }

    @Test
    void incrementCurrencySaturatesAtTheLowerBound() {
        UUID uuid = UUID.randomUUID();
        repository().save(sampleProfile(uuid));
        repository().setCurrency(uuid, Currency.COINS, Long.MIN_VALUE + 10L);

        EconomyResult result = repository().incrementCurrency(uuid, Currency.COINS, -100L, true);

        assertTrue(result.success());
        assertEquals(Long.MIN_VALUE, result.newBalance());
        assertEquals(Long.MIN_VALUE, repository().find(uuid).orElseThrow().balance(Currency.COINS));
    }

    @Test
    void incrementCurrencyOnUnknownProfileFails() {
        EconomyResult result = repository().incrementCurrency(UUID.randomUUID(), Currency.GEMS, 10L, true);

        assertFalse(result.success());
        assertEquals(EconomyResult.FailureReason.PROFILE_NOT_LOADED, result.failureReason());
        assertEquals(0L, result.newBalance());
    }

    @Test
    void incrementCurrencyOnUnknownProfileCreatesNothing() {
        UUID uuid = UUID.randomUUID();
        repository().incrementCurrency(uuid, Currency.COINS, 10L, true);
        repository().setCurrency(uuid, Currency.COINS, 10L);

        assertTrue(repository().find(uuid).isEmpty());
    }

    @Test
    void withdrawingAnUnusedCurrencyIsRefused() {
        UUID uuid = UUID.randomUUID();
        repository().save(sampleProfile(uuid));

        EconomyResult result = repository().incrementCurrency(uuid, Currency.of("tokens"), -1L, false);

        assertFalse(result.success());
        assertEquals(EconomyResult.FailureReason.INSUFFICIENT_FUNDS, result.failureReason());
        assertEquals(0L, result.newBalance());
    }

    @Test
    void setCurrencyWritesExactBalance() {
        UUID uuid = UUID.randomUUID();
        repository().save(sampleProfile(uuid));

        EconomyResult result = repository().setCurrency(uuid, Currency.GEMS, 77L);

        assertTrue(result.success());
        assertEquals(77L, result.newBalance());
        assertEquals(77L, repository().find(uuid).orElseThrow().balance(Currency.GEMS));
        assertEquals(100L, repository().find(uuid).orElseThrow().balance(Currency.COINS));
    }

    @Test
    void setCurrencyOnUnknownProfileFails() {
        EconomyResult result = repository().setCurrency(UUID.randomUUID(), Currency.COINS, 10L);

        assertFalse(result.success());
        assertEquals(EconomyResult.FailureReason.PROFILE_NOT_LOADED, result.failureReason());
    }

    @Test
    void customCurrenciesArePersisted() {
        UUID uuid = UUID.randomUUID();
        repository().save(sampleProfile(uuid));
        Currency tokens = Currency.of("tokens");

        repository().incrementCurrency(uuid, tokens, 12L, true);

        assertEquals(12L, repository().find(uuid).orElseThrow().balance(tokens));
        assertEquals(100L, repository().find(uuid).orElseThrow().balance(Currency.COINS));
    }

    @Test
    void concurrentIncrementsAreAtomic() throws InterruptedException {
        UUID uuid = UUID.randomUUID();
        repository().save(sampleProfile(uuid));
        int threads = 8;
        int incrementsPerThread = 25;
        CountDownLatch done = new CountDownLatch(threads);
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                executor.execute(() -> {
                    try {
                        for (int j = 0; j < incrementsPerThread; j++) {
                            repository().incrementCurrency(uuid, Currency.COINS, 1L, false);
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(done.await(60, TimeUnit.SECONDS));
        }

        assertEquals(100L + threads * incrementsPerThread,
                repository().find(uuid).orElseThrow().balance(Currency.COINS));
    }

    @Test
    void concurrentWithdrawalsNeverOverdraw() throws InterruptedException {
        UUID uuid = UUID.randomUUID();
        repository().save(sampleProfile(uuid));
        int threads = 8;
        int attemptsPerThread = 25;
        AtomicInteger succeeded = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(threads);
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                executor.execute(() -> {
                    try {
                        for (int j = 0; j < attemptsPerThread; j++) {
                            if (repository().incrementCurrency(uuid, Currency.COINS, -1L, false).success()) {
                                succeeded.incrementAndGet();
                            }
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(done.await(60, TimeUnit.SECONDS));
        }

        assertEquals(100, succeeded.get());
        assertEquals(0L, repository().find(uuid).orElseThrow().balance(Currency.COINS));
    }

    @Test
    void idsListEveryStoredProfile() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        repository().save(sampleProfile(first));
        repository().save(sampleProfile(second));

        assertTrue(repository().ids().containsAll(List.of(first, second)));
    }
}
