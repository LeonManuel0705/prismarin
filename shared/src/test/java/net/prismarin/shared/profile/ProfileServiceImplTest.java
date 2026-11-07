package net.prismarin.shared.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.shared.event.SimpleEventBus;
import net.prismarin.shared.storage.JsonFileProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileServiceImplTest {

    @TempDir
    Path dataDirectory;

    private JsonFileProfileRepository repository;
    private ExecutorService executor;
    private final SimpleEventBus events = new SimpleEventBus();
    private ProfileServiceImpl service;
    private final UUID uuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = new JsonFileProfileRepository(dataDirectory);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        service = new ProfileServiceImpl(repository, "de", executor, events);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void loadCreatesCachesAndPersistsFreshProfile() {
        PlayerProfile profile = service.load(uuid, "Leon").join();

        assertEquals("Leon", profile.getName());
        assertEquals("de", profile.getLocale());
        assertTrue(service.getCached(uuid).isPresent());
        assertTrue(repository.find(uuid).isPresent());
    }

    @Test
    void unloadPersistsFinalStateAndEvicts() {
        PlayerProfile profile = service.load(uuid, "Leon").join();
        profile.setLocale("en");

        service.unload(uuid).join();

        assertTrue(service.getCached(uuid).isEmpty());
        assertEquals("en", repository.find(uuid).orElseThrow().getLocale());
    }

    @Test
    void quickRelogNeverLosesTheCachedProfile() {
        service.load(uuid, "Leon").join();

        CompletableFuture<Void> unload = service.unload(uuid);
        CompletableFuture<PlayerProfile> reload = service.load(uuid, "Leon");
        CompletableFuture.allOf(unload, reload).join();

        assertTrue(service.getCached(uuid).isPresent());
    }

    @Test
    void concurrentModifiesOfUncachedProfileLoseNoUpdates() {
        repository.save(PlayerProfile.create(uuid, "Leon", "de"));
        int updates = 25;

        CompletableFuture<?>[] pending = new CompletableFuture<?>[updates];
        for (int i = 0; i < updates; i++) {
            pending[i] = service.modify(uuid, profile -> profile.setXp(profile.getXp() + 1L));
        }
        CompletableFuture.allOf(pending).join();

        assertEquals(updates, repository.find(uuid).orElseThrow().getXp());
    }

    @Test
    void savePreservesAtomicBalanceChanges() {
        PlayerProfile profile = service.load(uuid, "Leon").join();
        repository.incrementCurrency(uuid, Currency.COINS, 100L, true);

        service.save(profile).join();

        assertEquals(100L, repository.find(uuid).orElseThrow().balance(Currency.COINS));
    }
}
