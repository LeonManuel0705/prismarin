package net.prismarin.shared.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.profile.PlayerProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteProfileRepositoryTest extends ProfileRepositoryContract {

    @TempDir
    Path directory;

    private SqlDatabase database;
    private SqlProfileRepository repository;

    @BeforeEach
    void setUp() {
        database = SqlDatabase.sqlite(directory.resolve("prismarin.db"));
        repository = new SqlProfileRepository(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Override
    protected ProfileRepository repository() {
        return repository;
    }

    @Test
    void profilesSurviveReopeningTheDatabase() {
        UUID uuid = UUID.randomUUID();
        repository.save(new PlayerProfile(uuid, "Leon", Instant.ofEpochMilli(1L), Instant.ofEpochMilli(2L), "en",
                Map.of(Currency.COINS, 10L), 1, 0L, Map.of()));
        repository.incrementCurrency(uuid, Currency.COINS, 32L, false);
        database.close();

        database = SqlDatabase.sqlite(directory.resolve("prismarin.db"));
        repository = new SqlProfileRepository(database);

        PlayerProfile profile = repository.find(uuid).orElseThrow();
        assertEquals("en", profile.getLocale());
        assertEquals(42L, profile.balance(Currency.COINS));
    }

    @Test
    void schemaCreationIsIdempotent() {
        UUID uuid = UUID.randomUUID();
        repository.save(PlayerProfile.create(uuid, "Leon", "de"));

        SqlProfileRepository again = new SqlProfileRepository(database);

        assertEquals("Leon", again.find(uuid).orElseThrow().getName());
    }
}
