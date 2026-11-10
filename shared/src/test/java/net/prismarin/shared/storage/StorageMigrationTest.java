package net.prismarin.shared.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

class StorageMigrationTest {

    @TempDir
    Path directory;

    private CoreConfig config(CoreConfig.StorageMode mode) {
        return new CoreConfig("test", mode, "", "", false, "", 0, "", "de", Branding.DEFAULT,
                List.of(Currency.COINS, Currency.GEMS), Currency.COINS, Map.of(), Map.of(), true, SqlSettings.DEFAULT);
    }

    @Test
    void movesProfilesBalancesAndDocumentsFromJsonToSqlite() {
        UUID player = UUID.randomUUID();
        try (ProfileStorage json = ProfileStorage.create(config(CoreConfig.StorageMode.LOCAL), directory);
             ProfileStorage sqlite = ProfileStorage.create(config(CoreConfig.StorageMode.SQLITE), directory)) {
            json.repository().save(PlayerProfile.create(player, "Leon", "de"));
            json.repository().incrementCurrency(player, Currency.COINS, 120L, true);
            json.repository().incrementCurrency(player, Currency.GEMS, 4L, true);
            json.documents().save("punishments", "abc", "{\"id\":\"abc\"}");
            json.documents().save("names", "leon", "{\"uuid\":\"" + player + "\"}");

            StorageMigration.Result result = StorageMigration.run(json, sqlite);

            assertEquals(1, result.profiles());
            assertEquals(2, result.balances());
            assertEquals(2, result.documents());
            PlayerProfile moved = sqlite.repository().find(player).orElseThrow();
            assertEquals("Leon", moved.getName());
            assertEquals(120L, moved.balance(Currency.COINS));
            assertEquals(4L, moved.balance(Currency.GEMS));
            assertEquals(Optional.of("{\"id\":\"abc\"}"), sqlite.documents().find("punishments", "abc"));
        }
    }
}
