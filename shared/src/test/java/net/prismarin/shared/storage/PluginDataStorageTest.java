package net.prismarin.shared.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.prismarin.api.brand.Branding;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.storage.DataCollection;
import net.prismarin.api.storage.DataStorage;
import net.prismarin.api.storage.TypedCollection;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.config.SqlSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginDataStorageTest {

    private static final Pattern STORE_NAME = Pattern.compile("[a-z0-9_-]{1,64}");
    private static final List<String> CORE_COLLECTIONS = List.of("names", "punishments", "reports", "report-feedback",
            "audit", "maintenance", "friends", "presence", "nick-claims", "nick-owners", "nick-skins",
            "punishment-meta");

    record Stats(int wins, int games) {
    }

    @TempDir
    Path directory;

    @Test
    void physicalNamesFitEveryStoreAndNeverHitCoreCollections() {
        String longest = PluginCollections.physical("n".repeat(24), "c".repeat(32));
        assertTrue(STORE_NAME.matcher(longest).matches(), longest);
        assertEquals("ext_bedwars_stats", PluginCollections.physical("BedWars", "stats"));
        for (String core : CORE_COLLECTIONS) {
            assertFalse(PluginCollections.owned(core), core);
        }
        assertTrue(PluginCollections.owned(longest));
    }

    @Test
    void differentNamespaceAndCollectionPairsNeverShareAName() {
        assertFalse(PluginCollections.physical("ab", "c-d").equals(PluginCollections.physical("ab-c", "d")));
        assertFalse(PluginCollections.physical("ab", "cd").equals(PluginCollections.physical("abc", "d")));
    }

    @Test
    void collectionsStoreDocumentsUnderTheirNamespace() {
        JsonFileDocumentStore store = new JsonFileDocumentStore(directory);
        DataStorage storage = new PluginDataStorage(store, Runnable::run);
        DataCollection stats = storage.namespace("bedwars").collection("stats");
        DataCollection otherStats = storage.namespace("skywars").collection("stats");

        stats.save("leon", "{\"wins\":3}").join();
        otherStats.save("leon", "{\"wins\":9}").join();

        assertEquals("bedwars", stats.namespace());
        assertEquals("stats", stats.name());
        assertEquals(Optional.of("{\"wins\":3}"), stats.find("leon").join());
        assertEquals(Optional.of("{\"wins\":3}"), store.find("ext_bedwars_stats", "leon"));
        assertEquals(Optional.of("{\"wins\":9}"), otherStats.find("leon").join());
        assertEquals(List.of("ext_bedwars_stats", "ext_skywars_stats"), PluginCollections.in(store));
        assertTrue(stats.exists("leon").join());
        assertTrue(stats.delete("leon").join());
        assertFalse(stats.exists("leon").join());
        assertEquals(Optional.of("{\"wins\":9}"), otherStats.find("leon").join());
    }

    @Test
    void listAndFindByWorkOnRawAndTypedCollections() {
        DataStorage storage = new PluginDataStorage(new JsonFileDocumentStore(directory), Runnable::run);
        TypedCollection<Stats> stats = storage.namespace("bedwars").collection("stats", Stats.class);

        stats.save("tim", new Stats(1, 4)).join();
        stats.save("leon", new Stats(3, 4)).join();

        Map<String, Stats> all = stats.list().join();
        assertEquals(Map.of("leon", new Stats(3, 4), "tim", new Stats(1, 4)), all);
        assertEquals(List.of(new Stats(3, 4)), stats.findBy("wins", "3").join());
        assertEquals(2, stats.findBy("games", "4").join().size());
        assertEquals(2, stats.ids().join().size());
    }

    @Test
    void invalidInputFailsBeforeAnyStorageAccess() {
        DataStorage storage = new PluginDataStorage(new JsonFileDocumentStore(directory), Runnable::run);
        DataCollection stats = storage.namespace("bedwars").collection("stats");

        assertThrows(IllegalArgumentException.class, () -> storage.namespace("prismarin"));
        assertThrows(IllegalArgumentException.class, () -> storage.namespace("bed_wars"));
        assertThrows(IllegalArgumentException.class, () -> storage.namespace("bedwars").collection("round_stats"));
        assertThrows(IllegalArgumentException.class, () -> stats.save("", "{}"));
        assertThrows(IllegalArgumentException.class, () -> stats.find("x".repeat(129)));
        assertThrows(NullPointerException.class, () -> stats.save("leon", null));
        assertThrows(IllegalArgumentException.class, () -> stats.findBy("", "x"));
    }

    @Test
    void migrationCarriesPluginCollections() {
        CoreConfig local = config(CoreConfig.StorageMode.LOCAL);
        CoreConfig sqlite = config(CoreConfig.StorageMode.SQLITE);
        try (ProfileStorage source = ProfileStorage.create(local, directory);
             ProfileStorage target = ProfileStorage.create(sqlite, directory)) {
            new PluginDataStorage(source.documents(), Runnable::run).namespace("bedwars").collection("stats")
                    .save("leon", "{\"wins\":3}").join();
            source.documents().save("audit", "1", "{}");

            StorageMigration.Result result = StorageMigration.run(source, target);

            assertEquals(2, result.documents());
            assertEquals(Optional.of("{\"wins\":3}"), new PluginDataStorage(target.documents(), Runnable::run)
                    .namespace("bedwars").collection("stats").find("leon").join());
        }
    }

    private static CoreConfig config(CoreConfig.StorageMode mode) {
        return new CoreConfig("test", mode, "", "", false, "", 0, "", "de", Branding.DEFAULT,
                List.of(Currency.COINS), Currency.COINS, Map.of(), Map.of(), true, SqlSettings.DEFAULT);
    }
}
