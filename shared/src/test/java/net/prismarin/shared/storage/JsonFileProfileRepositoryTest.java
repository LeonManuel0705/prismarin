package net.prismarin.shared.storage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import net.prismarin.api.profile.PlayerProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFileProfileRepositoryTest {

    @TempDir
    Path dataDirectory;

    private JsonFileProfileRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JsonFileProfileRepository(dataDirectory);
    }

    private PlayerProfile sampleProfile(UUID uuid) {
        Map<String, String> settings = new HashMap<>();
        settings.put("scoreboard", "true");
        return new PlayerProfile(uuid, "Leon", Instant.ofEpochMilli(1000L), Instant.ofEpochMilli(2000L),
                "de", 100L, 5L, 3, 200L, settings);
    }

    @Test
    void roundtripPreservesAllFields() {
        UUID uuid = UUID.randomUUID();
        repository.upsert(sampleProfile(uuid));

        Optional<PlayerProfile> found = repository.find(uuid);

        assertTrue(found.isPresent());
        PlayerProfile profile = found.get();
        assertEquals(uuid, profile.getUuid());
        assertEquals("Leon", profile.getName());
        assertEquals(Instant.ofEpochMilli(1000L), profile.getFirstJoin());
        assertEquals(Instant.ofEpochMilli(2000L), profile.getLastSeen());
        assertEquals("de", profile.getLocale());
        assertEquals(100L, profile.getCoins());
        assertEquals(5L, profile.getGems());
        assertEquals(3, profile.getLevel());
        assertEquals(200L, profile.getXp());
        assertEquals("true", profile.getSettings().get("scoreboard"));
    }

    @Test
    void findOfUnknownUuidIsEmpty() {
        assertTrue(repository.find(UUID.randomUUID()).isEmpty());
    }

    @Test
    void incrementCurrencyAppliesDelta() {
        UUID uuid = UUID.randomUUID();
        repository.upsert(sampleProfile(uuid));

        EconomyResult result = repository.incrementCurrency(uuid, Currency.COINS, 50L, true);

        assertTrue(result.success());
        assertEquals(150L, result.newBalance());
        assertEquals(150L, repository.find(uuid).orElseThrow().getCoins());
    }

    @Test
    void incrementCurrencyGuardsAgainstNegativeBalance() {
        UUID uuid = UUID.randomUUID();
        repository.upsert(sampleProfile(uuid));

        EconomyResult result = repository.incrementCurrency(uuid, Currency.COINS, -200L, false);

        assertFalse(result.success());
        assertEquals(EconomyResult.FailureReason.INSUFFICIENT_FUNDS, result.failureReason());
        assertEquals(100L, result.newBalance());
        assertEquals(100L, repository.find(uuid).orElseThrow().getCoins());
    }

    @Test
    void incrementCurrencyAllowsNegativeWhenPermitted() {
        UUID uuid = UUID.randomUUID();
        repository.upsert(sampleProfile(uuid));

        EconomyResult result = repository.incrementCurrency(uuid, Currency.COINS, -200L, true);

        assertTrue(result.success());
        assertEquals(-100L, result.newBalance());
    }

    @Test
    void incrementCurrencyOnUnknownProfileFails() {
        EconomyResult result = repository.incrementCurrency(UUID.randomUUID(), Currency.GEMS, 10L, true);

        assertFalse(result.success());
        assertEquals(EconomyResult.FailureReason.PROFILE_NOT_LOADED, result.failureReason());
    }

    @Test
    void concurrentIncrementsAreAtomic() throws InterruptedException {
        UUID uuid = UUID.randomUUID();
        repository.upsert(sampleProfile(uuid));
        int threads = 8;
        int incrementsPerThread = 25;
        CountDownLatch done = new CountDownLatch(threads);
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                executor.execute(() -> {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        repository.incrementCurrency(uuid, Currency.COINS, 1L, false);
                    }
                    done.countDown();
                });
            }
            assertTrue(done.await(30, TimeUnit.SECONDS));
        }

        assertEquals(100L + threads * incrementsPerThread, repository.find(uuid).orElseThrow().getCoins());
    }
}
