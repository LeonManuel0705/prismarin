package net.prismarin.shared.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.brand.Branding;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.config.SqlSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileStorageTest {

    @TempDir
    Path directory;

    private static CoreConfig config(CoreConfig.StorageMode mode, SqlSettings sql) {
        return new CoreConfig("test", mode, "", "", false, "", 0, "", "de", Branding.DEFAULT,
                List.of(Currency.COINS), Currency.COINS, Map.of(), Map.of(), true, sql);
    }

    @Test
    void localModeUsesJsonFiles() {
        try (ProfileStorage storage = ProfileStorage.create(config(CoreConfig.StorageMode.LOCAL,
                SqlSettings.DEFAULT), directory)) {
            assertInstanceOf(JsonFileProfileRepository.class, storage.repository());
            assertInstanceOf(JsonFileDocumentStore.class, storage.documents());
        }
    }

    @Test
    void sqliteModeKeepsProfilesAndDocumentsInOneDatabaseFile() {
        SqlSettings sql = new SqlSettings("localhost", 3306, "prismarin", "prismarin", "", 8, Map.of(),
                "data/network.db");
        CoreConfig config = config(CoreConfig.StorageMode.SQLITE, sql);
        UUID uuid = UUID.randomUUID();
        try (ProfileStorage storage = ProfileStorage.create(config, directory)) {
            assertInstanceOf(SqlProfileRepository.class, storage.repository());
            assertInstanceOf(SqlDocumentStore.class, storage.documents());
            storage.repository().save(PlayerProfile.create(uuid, "Leon", "de"));
            storage.repository().incrementCurrency(uuid, Currency.COINS, 25L, false);
            storage.documents().save("names", "leon", "{\"uuid\":\"" + uuid + "\"}");
        }

        assertTrue(Files.exists(directory.resolve("data").resolve("network.db")));
        try (ProfileStorage storage = ProfileStorage.create(config, directory)) {
            assertEquals(25L, storage.repository().find(uuid).orElseThrow().balance(Currency.COINS));
            assertEquals(Optional.of("{\"uuid\":\"" + uuid + "\"}"), storage.documents().find("names", "leon"));
        }
    }
}
