package net.prismarin.shared.storage;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import net.prismarin.api.profile.PlayerProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                "de", 100L, 5L, 3, 200L, new HashMap<>());
    }

    @Test
    void roundtripPreservesAllFields() {
        UUID uuid = UUID.randomUUID();
        repository.upsert(sampleProfile(uuid));

        Optional<PlayerProfile> found = repository.find(uuid);

        assertTrue(found.isPresent());
        PlayerProfile profile = found.get();
        assertEquals("Leon", profile.getName());
        assertEquals(Instant.ofEpochMilli(1000L), profile.getFirstJoin());
        assertEquals(100L, profile.getCoins());
        assertEquals(5L, profile.getGems());
        assertEquals(3, profile.getLevel());
        assertEquals(200L, profile.getXp());
    }

    @Test
    void upsertReplacesExistingDocument() {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = sampleProfile(uuid);
        repository.upsert(profile);
        profile.setCoins(999L);
        repository.upsert(profile);

        assertEquals(999L, repository.find(uuid).orElseThrow().getCoins());
    }

    @Test
    void incrementCurrencyIsAtomicAndGuardsNegativeBalance() {
        UUID uuid = UUID.randomUUID();
        repository.upsert(sampleProfile(uuid));

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
    void incrementCurrencyOnUnknownProfileFails() {
        EconomyResult result = repository.incrementCurrency(UUID.randomUUID(), Currency.GEMS, 10L, true);

        assertFalse(result.success());
        assertEquals(EconomyResult.FailureReason.PROFILE_NOT_LOADED, result.failureReason());
    }
}
