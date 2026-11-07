package net.prismarin.shared.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import net.prismarin.api.profile.PlayerProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Tag("integration")
@Testcontainers
class MongoProfileRepositoryIT {

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");

    private MongoClient client;
    private MongoProfileRepository repository;

    @BeforeEach
    void setUp() {
        client = MongoClients.create(MONGO.getConnectionString());
        repository = new MongoProfileRepository(client.getDatabase("prismarin-it"));
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    private PlayerProfile sampleProfile(UUID uuid) {
        return new PlayerProfile(uuid, "Leon", Instant.ofEpochMilli(1000L), Instant.ofEpochMilli(2000L),
                "de", Map.of(Currency.COINS, 100L, Currency.GEMS, 5L), 3, 200L, new HashMap<>());
    }

    @Test
    void roundtripPreservesAllFields() {
        UUID uuid = UUID.randomUUID();
        repository.save(sampleProfile(uuid));

        Optional<PlayerProfile> found = repository.find(uuid);

        assertTrue(found.isPresent());
        PlayerProfile profile = found.get();
        assertEquals("Leon", profile.getName());
        assertEquals(Instant.ofEpochMilli(1000L), profile.getFirstJoin());
        assertEquals(100L, profile.balance(Currency.COINS));
        assertEquals(5L, profile.balance(Currency.GEMS));
        assertEquals(3, profile.getLevel());
        assertEquals(200L, profile.getXp());
    }

    @Test
    void saveDoesNotOverwriteStoredBalances() {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = sampleProfile(uuid);
        repository.save(profile);
        repository.incrementCurrency(uuid, Currency.COINS, 400L, true);

        profile.balance(Currency.COINS, 999L);
        profile.setName("LeonNeu");
        repository.save(profile);

        PlayerProfile stored = repository.find(uuid).orElseThrow();
        assertEquals("LeonNeu", stored.getName());
        assertEquals(500L, stored.balance(Currency.COINS));
        assertEquals(5L, stored.balance(Currency.GEMS));
    }

    @Test
    void incrementCurrencyIsAtomicAndGuardsNegativeBalance() {
        UUID uuid = UUID.randomUUID();
        repository.save(sampleProfile(uuid));

        EconomyResult deposit = repository.incrementCurrency(uuid, Currency.COINS, 50L, true);
        assertTrue(deposit.success());
        assertEquals(150L, deposit.newBalance());

        EconomyResult overdraw = repository.incrementCurrency(uuid, Currency.COINS, -200L, false);
        assertFalse(overdraw.success());
        assertEquals(EconomyResult.FailureReason.INSUFFICIENT_FUNDS, overdraw.failureReason());
        assertEquals(150L, overdraw.newBalance());

        EconomyResult withdraw = repository.incrementCurrency(uuid, Currency.COINS, -150L, false);
        assertTrue(withdraw.success());
        assertEquals(0L, withdraw.newBalance());
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
}
