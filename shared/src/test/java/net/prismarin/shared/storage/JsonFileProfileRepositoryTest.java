package net.prismarin.shared.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
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
                "de", Map.of(Currency.COINS, 100L, Currency.GEMS, 5L), 3, 200L, settings);
    }

    @Test
    void roundtripPreservesAllFields() {
        UUID uuid = UUID.randomUUID();
        repository.save(sampleProfile(uuid));

        Optional<PlayerProfile> found = repository.find(uuid);

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
        assertEquals("true", profile.getSettings().get("scoreboard"));
    }

    @Test
    void findOfUnknownUuidIsEmpty() {
        assertTrue(repository.find(UUID.randomUUID()).isEmpty());
    }

    @Test
    void saveDoesNotOverwriteStoredBalances() {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = sampleProfile(uuid);
        repository.save(profile);
        repository.incrementCurrency(uuid, Currency.COINS, 400L, true);

        profile.setName("LeonNeu");
        repository.save(profile);

        PlayerProfile stored = repository.find(uuid).orElseThrow();
        assertEquals("LeonNeu", stored.getName());
        assertEquals(500L, stored.balance(Currency.COINS));
        assertEquals(5L, stored.balance(Currency.GEMS));
    }

    @Test
    void incrementCurrencyAppliesDelta() {
        UUID uuid = UUID.randomUUID();
        repository.save(sampleProfile(uuid));

        EconomyResult result = repository.incrementCurrency(uuid, Currency.COINS, 50L, true);

        assertTrue(result.success());
        assertEquals(150L, result.newBalance());
        assertEquals(150L, repository.find(uuid).orElseThrow().balance(Currency.COINS));
    }

    @Test
    void incrementCurrencyGuardsAgainstNegativeBalance() {
        UUID uuid = UUID.randomUUID();
        repository.save(sampleProfile(uuid));

        EconomyResult result = repository.incrementCurrency(uuid, Currency.COINS, -200L, false);

        assertFalse(result.success());
        assertEquals(EconomyResult.FailureReason.INSUFFICIENT_FUNDS, result.failureReason());
        assertEquals(100L, result.newBalance());
        assertEquals(100L, repository.find(uuid).orElseThrow().balance(Currency.COINS));
    }

    @Test
    void incrementCurrencyAllowsNegativeWhenPermitted() {
        UUID uuid = UUID.randomUUID();
        repository.save(sampleProfile(uuid));

        EconomyResult result = repository.incrementCurrency(uuid, Currency.COINS, -200L, true);

        assertTrue(result.success());
        assertEquals(-100L, result.newBalance());
    }

    @Test
    void incrementCurrencySaturatesInsteadOfOverflowing() {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = sampleProfile(uuid);
        profile.balance(Currency.COINS, Long.MAX_VALUE - 10L);
        repository.save(profile);

        EconomyResult result = repository.incrementCurrency(uuid, Currency.COINS, 100L, true);

        assertTrue(result.success());
        assertEquals(Long.MAX_VALUE, result.newBalance());
        assertEquals(Long.MAX_VALUE, repository.find(uuid).orElseThrow().balance(Currency.COINS));
    }

    @Test
    void incrementCurrencyOnUnknownProfileFails() {
        EconomyResult result = repository.incrementCurrency(UUID.randomUUID(), Currency.GEMS, 10L, true);

        assertFalse(result.success());
        assertEquals(EconomyResult.FailureReason.PROFILE_NOT_LOADED, result.failureReason());
    }

    @Test
    void setCurrencyWritesExactBalance() {
        UUID uuid = UUID.randomUUID();
        repository.save(sampleProfile(uuid));

        EconomyResult result = repository.setCurrency(uuid, Currency.GEMS, 77L);

        assertTrue(result.success());
        assertEquals(77L, result.newBalance());
        assertEquals(77L, repository.find(uuid).orElseThrow().balance(Currency.GEMS));
    }

    @Test
    void setCurrencyOnUnknownProfileFails() {
        EconomyResult result = repository.setCurrency(UUID.randomUUID(), Currency.COINS, 10L);

        assertFalse(result.success());
        assertEquals(EconomyResult.FailureReason.PROFILE_NOT_LOADED, result.failureReason());
    }

    @Test
    void concurrentIncrementsAreAtomic() throws InterruptedException {
        UUID uuid = UUID.randomUUID();
        repository.save(sampleProfile(uuid));
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

        assertEquals(100L + threads * incrementsPerThread, repository.find(uuid).orElseThrow().balance(Currency.COINS));
    }

    @Test
    void legacyCoinAndGemFieldsAreMigrated() throws IOException {
        UUID uuid = UUID.randomUUID();
        Files.writeString(dataDirectory.resolve("profiles").resolve(uuid + ".json"), "{\"uuid\":\"" + uuid
                + "\",\"name\":\"Leon\",\"firstJoin\":1,\"lastSeen\":2,\"locale\":\"de\","
                + "\"coins\":420,\"gems\":7,\"level\":1,\"xp\":5,\"settings\":{}}");

        PlayerProfile profile = repository.find(uuid).orElseThrow();
        assertEquals(420L, profile.balance(Currency.COINS));
        assertEquals(7L, profile.balance(Currency.GEMS));

        repository.incrementCurrency(uuid, Currency.COINS, 80L, true);
        String stored = Files.readString(dataDirectory.resolve("profiles").resolve(uuid + ".json"));
        assertEquals(500L, repository.find(uuid).orElseThrow().balance(Currency.COINS));
        assertEquals(7L, repository.find(uuid).orElseThrow().balance(Currency.GEMS));
        assertTrue(stored.contains("\"balances\""));
        assertFalse(stored.contains("\"coins\":420"));
    }

    @Test
    void customCurrenciesArePersisted() {
        UUID uuid = UUID.randomUUID();
        repository.save(sampleProfile(uuid));
        Currency tokens = Currency.of("tokens");
        repository.incrementCurrency(uuid, tokens, 12L, true);
        assertEquals(12L, repository.find(uuid).orElseThrow().balance(tokens));
        assertEquals(100L, repository.find(uuid).orElseThrow().balance(Currency.COINS));
    }
}
