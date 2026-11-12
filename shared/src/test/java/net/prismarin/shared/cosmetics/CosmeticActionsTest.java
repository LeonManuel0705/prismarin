package net.prismarin.shared.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticEquipEvent;
import net.prismarin.api.cosmetics.CosmeticHat;
import net.prismarin.api.cosmetics.CosmeticParticles;
import net.prismarin.api.cosmetics.CosmeticPurchaseEvent;
import net.prismarin.api.cosmetics.CosmeticResult;
import net.prismarin.api.cosmetics.CosmeticResult.Status;
import net.prismarin.api.cosmetics.CosmeticSlot;
import net.prismarin.api.cosmetics.CosmeticSource;
import net.prismarin.api.cosmetics.CosmeticUnequippedEvent;
import net.prismarin.api.cosmetics.CosmeticUnlockedEvent;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.shared.economy.DisabledEconomyService;
import net.prismarin.shared.economy.EconomyServiceImpl;
import net.prismarin.shared.event.SimpleEventBus;
import net.prismarin.shared.storage.DocumentStore;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import net.prismarin.shared.storage.JsonFileProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CosmeticActionsTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path directory;

    private final Set<String> permitted = ConcurrentHashMap.newKeySet();
    private final List<PrismarinEvent> seen = new CopyOnWriteArrayList<>();
    private ExecutorService io;
    private SimpleEventBus events;
    private EconomyService economy;
    private CosmeticCatalog catalog;
    private FlakyStore store;

    private static final class FlakyStore implements DocumentStore {

        private final DocumentStore delegate;
        private volatile boolean failing;

        private FlakyStore(DocumentStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public void save(String collection, String id, String json) {
            delegate.save(collection, id, json);
        }

        @Override
        public Optional<String> find(String collection, String id) {
            return delegate.find(collection, id);
        }

        @Override
        public List<String> findAll(String collection) {
            return delegate.findAll(collection);
        }

        @Override
        public Map<String, String> entries(String collection) {
            return delegate.entries(collection);
        }

        @Override
        public List<String> findBy(String collection, String field, String value) {
            return delegate.findBy(collection, field, value);
        }

        @Override
        public boolean delete(String collection, String id) {
            return delegate.delete(collection, id);
        }

        @Override
        public Optional<String> update(String collection, String id, UnaryOperator<String> change) {
            if (failing) {
                throw new IllegalStateException("the database is gone");
            }
            return delegate.update(collection, id, change);
        }

        @Override
        public List<String> collections() {
            return delegate.collections();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    @BeforeEach
    void setUp() {
        io = Executors.newVirtualThreadPerTaskExecutor();
        events = new SimpleEventBus();
        events.subscribe(PrismarinEvent.class, seen::add);
        JsonFileProfileRepository profiles = new JsonFileProfileRepository(directory.resolve("profiles"));
        profiles.save(PlayerProfile.create(PLAYER, "Leon", "en"));
        economy = new EconomyServiceImpl(profiles, io, List.of(Currency.COINS, Currency.GEMS), Currency.COINS,
                events);
        catalog = new CosmeticCatalog();
        catalog.load(List.of(
                        trail("flame").withPrice("coins", 500),
                        trail("hearts").withPrice("coins", 1000),
                        trail("gift"),
                        trail("tokens").withPrice("tokens", 5),
                        Cosmetic.particles("wings", CosmeticSlot.AURA, "Wings",
                                CosmeticParticles.of("wings", "dust")).withPrice("gems", 80),
                        Cosmetic.particles("halo", CosmeticSlot.AURA, "Halo", CosmeticParticles.of("halo", "dust"))
                                .withPermission("prismarin.cosmetics.vip"),
                        Cosmetic.hat("crown", "Crown", CosmeticHat.item("golden_helmet")).withPrice("coins", 300),
                        Cosmetic.particles("skulls", CosmeticSlot.KILL_EFFECT, "Skulls",
                                CosmeticParticles.of("burst", "smoke")).withPrice("coins", 10)),
                List.of(new CosmeticCategory(CosmeticSlot.KILL_EFFECT, false, null, null, null, 0, List.of())),
                CosmeticCatalogTest.RARITIES);
        store = new FlakyStore(new JsonFileDocumentStore(directory));
        coins(1000);
        gems(10);
        seen.clear();
    }

    @AfterEach
    void tearDown() {
        io.shutdown();
    }

    private static Cosmetic trail(String id) {
        return CosmeticCatalogTest.trail(id);
    }

    private CosmeticActions actions() {
        return actions(economy);
    }

    private CosmeticActions actions(EconomyService service) {
        return new CosmeticActions(catalog, new CosmeticLedger(store, CLOCK), service, events, io, Runnable::run,
                (player, cosmetic) -> permitted.contains(cosmetic.id()));
    }

    private void coins(long amount) {
        economy.set(PLAYER, Currency.COINS, amount).join();
    }

    private void gems(long amount) {
        economy.set(PLAYER, Currency.GEMS, amount).join();
    }

    private long balance(Currency currency) {
        return economy.balance(PLAYER, currency).join();
    }

    private CosmeticResult buy(String id) {
        return actions().purchase(PLAYER, "Leon", id).join().result();
    }

    private <E> List<E> events(Class<E> type) {
        return seen.stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    void buyingTakesThePriceAndUnlocksTheCosmetic() {
        CosmeticActions.Outcome outcome = actions().purchase(PLAYER, "Leon", "flame").join();

        assertEquals(Status.DONE, outcome.result().status());
        assertEquals(500L, balance(Currency.COINS));
        assertTrue(outcome.state().owns("flame"));
        assertEquals(CosmeticSource.PURCHASE, outcome.state().owned().get("flame").source());
        assertEquals(1, events(CosmeticPurchaseEvent.class).size());
        assertEquals(List.of(new CosmeticUnlockedEvent(PLAYER, catalog.find("flame").orElseThrow(),
                CosmeticSource.PURCHASE)), events(CosmeticUnlockedEvent.class));
    }

    @Test
    void notEnoughCoinsTellsWhatIsMissingAndTakesNothing() {
        coins(100);

        CosmeticResult result = buy("flame");

        assertEquals(Status.NOT_ENOUGH_MONEY, result.status());
        assertEquals(400L, result.missing());
        assertEquals(100L, balance(Currency.COINS));
        assertFalse(actions().state(PLAYER).join().owns("flame"));
        assertTrue(events(CosmeticUnlockedEvent.class).isEmpty());
    }

    @Test
    void everyCurrencyIsCheckedOnItsOwn() {
        coins(1_000_000);
        gems(10);

        CosmeticResult wings = buy("wings");

        assertEquals(Status.NOT_ENOUGH_MONEY, wings.status());
        assertEquals(70L, wings.missing());
        assertEquals(1_000_000L, balance(Currency.COINS));
        assertEquals(10L, balance(Currency.GEMS));

        gems(80);
        assertEquals(Status.DONE, buy("wings").status());
        assertEquals(0L, balance(Currency.GEMS));
        assertEquals(1_000_000L, balance(Currency.COINS));
    }

    @Test
    void aCosmeticIsNeverPaidForTwice() {
        assertEquals(Status.DONE, buy("flame").status());

        CosmeticResult again = buy("flame");

        assertEquals(Status.ALREADY_OWNED, again.status());
        assertEquals(500L, balance(Currency.COINS));
        assertEquals(1, events(CosmeticPurchaseEvent.class).size());
    }

    @Test
    void aPermissionCountsAsOwned() {
        permitted.add("flame");

        assertEquals(Status.ALREADY_OWNED, buy("flame").status());
        assertEquals(1000L, balance(Currency.COINS));
        assertTrue(events(CosmeticPurchaseEvent.class).isEmpty());
    }

    @Test
    void aCancelledPurchaseTakesNothing() {
        events.subscribe(CosmeticPurchaseEvent.class, event -> event.setCancelled(true));

        assertEquals(Status.CANCELLED, buy("crown").status());
        assertEquals(1000L, balance(Currency.COINS));
        assertFalse(actions().state(PLAYER).join().owns("crown"));
    }

    @Test
    void theMoneyComesBackWhenTheStorageFails() {
        events.subscribe(CosmeticPurchaseEvent.class, event -> store.failing = true);

        CosmeticResult result = buy("flame");

        store.failing = false;
        assertEquals(Status.FAILED, result.status());
        assertEquals(1000L, balance(Currency.COINS));
        assertFalse(actions().state(PLAYER).join().owns("flame"));
    }

    @Test
    void theMoneyComesBackWhenAnotherServerWasFaster() {
        CosmeticLedger otherServer = new CosmeticLedger(store, CLOCK);
        events.subscribe(CosmeticPurchaseEvent.class, event -> otherServer.grant(PLAYER, "Leon", "flame",
                CosmeticSource.PURCHASE));

        CosmeticResult result = buy("flame");

        assertEquals(Status.ALREADY_OWNED, result.status());
        assertEquals(1000L, balance(Currency.COINS));
        assertTrue(events(CosmeticUnlockedEvent.class).isEmpty());
    }

    @Test
    void parallelPurchasesPayExactlyOnce() {
        coins(100_000);
        CosmeticActions actions = actions();
        List<CompletableFuture<CosmeticActions.Outcome>> purchases = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            purchases.add(CompletableFuture.supplyAsync(() -> actions.purchase(PLAYER, "Leon", "hearts"), io)
                    .thenCompose(future -> future));
        }
        CompletableFuture.allOf(purchases.toArray(CompletableFuture[]::new)).join();

        long done = purchases.stream().filter(future -> future.join().result().success()).count();
        assertEquals(1L, done);
        assertTrue(purchases.stream().map(future -> future.join().result().status())
                .allMatch(status -> status == Status.DONE || status == Status.ALREADY_OWNED));
        assertEquals(99_000L, balance(Currency.COINS));
    }

    @Test
    void someCosmeticsCannotBeBought() {
        assertEquals(Status.NOT_FOR_SALE, buy("gift").status());
        assertEquals(Status.NOT_FOR_SALE, buy("halo").status());
        assertEquals(Status.NOT_FOR_SALE, buy("tokens").status());
        assertEquals(Status.CATEGORY_OFF, buy("skulls").status());
        assertEquals(Status.UNKNOWN, buy("nothing").status());
        assertEquals(Status.NOT_FOR_SALE, actions(new DisabledEconomyService()).purchase(PLAYER, "Leon", "flame")
                .join().result().status());
        assertEquals(1000L, balance(Currency.COINS));
    }

    @Test
    void onlyOwnedCosmeticsCanBeWorn() {
        assertEquals(Status.NOT_OWNED, actions().equip(PLAYER, "flame").join().result().status());

        actions().unlock(PLAYER, "Leon", "flame", CosmeticSource.REWARD).join();
        CosmeticActions.Outcome worn = actions().equip(PLAYER, "flame").join();

        assertEquals(Status.DONE, worn.result().status());
        assertEquals(Optional.of("flame"), worn.state().equipped(CosmeticSlot.TRAIL));
        assertEquals(Status.ALREADY_EQUIPPED, actions().equip(PLAYER, "flame").join().result().status());
    }

    @Test
    void permissionCosmeticsCanBeWornWithoutOwningThem() {
        permitted.add("halo");

        assertEquals(Status.DONE, actions().equip(PLAYER, "halo").join().result().status());
        permitted.remove("halo");
        assertEquals(Status.NOT_OWNED, actions().equip(PLAYER, "wings").join().result().status());
    }

    @Test
    void eachSlotHoldsOneCosmeticAndSlotsDoNotTouchEachOther() {
        CosmeticActions actions = actions();
        for (String id : List.of("flame", "hearts", "crown")) {
            actions.unlock(PLAYER, "Leon", id, CosmeticSource.STAFF).join();
        }
        actions.equip(PLAYER, "flame").join();
        actions.equip(PLAYER, "crown").join();
        seen.clear();

        CosmeticActions.Outcome swapped = actions.equip(PLAYER, "hearts").join();

        assertEquals(Map.of(CosmeticSlot.TRAIL, "hearts", CosmeticSlot.HAT, "crown"), swapped.state().equipped());
        assertEquals(List.of(new CosmeticUnequippedEvent(PLAYER, "flame", CosmeticSlot.TRAIL,
                CosmeticUnequippedEvent.Reason.REPLACED)), events(CosmeticUnequippedEvent.class));
        CosmeticEquipEvent equip = events(CosmeticEquipEvent.class).getFirst();
        assertEquals(Optional.of("flame"), equip.previous());
        assertEquals(CosmeticSlot.TRAIL, equip.slot());
    }

    @Test
    void aCancelledEquipChangesNothing() {
        actions().unlock(PLAYER, "Leon", "flame", CosmeticSource.STAFF).join();
        events.subscribe(CosmeticEquipEvent.class, event -> event.setCancelled(true));

        assertEquals(Status.CANCELLED, actions().equip(PLAYER, "flame").join().result().status());
        assertTrue(actions().state(PLAYER).join().equipped().isEmpty());
    }

    @Test
    void switchedOffCategoriesCannotBeWorn() {
        actions().unlock(PLAYER, "Leon", "skulls", CosmeticSource.STAFF).join();

        assertEquals(Status.CATEGORY_OFF, actions().equip(PLAYER, "skulls").join().result().status());
    }

    @Test
    void takingOffAndRevokingFireTheirEvents() {
        CosmeticActions actions = actions();
        actions.unlock(PLAYER, "Leon", "flame", CosmeticSource.STAFF).join();
        actions.unlock(PLAYER, "Leon", "crown", CosmeticSource.STAFF).join();
        actions.equip(PLAYER, "flame").join();
        actions.equip(PLAYER, "crown").join();
        seen.clear();

        assertEquals(Status.DONE, actions.unequip(PLAYER, CosmeticSlot.HAT).join().result().status());
        assertEquals(Status.NOT_EQUIPPED, actions.unequip(PLAYER, CosmeticSlot.HAT).join().result().status());
        CosmeticActions.Outcome revoked = actions.revoke(PLAYER, "flame").join();

        assertEquals(Status.DONE, revoked.result().status());
        assertTrue(revoked.state().equipped().isEmpty());
        assertEquals(List.of(
                new CosmeticUnequippedEvent(PLAYER, "crown", CosmeticSlot.HAT,
                        CosmeticUnequippedEvent.Reason.UNEQUIPPED),
                new CosmeticUnequippedEvent(PLAYER, "flame", CosmeticSlot.TRAIL,
                        CosmeticUnequippedEvent.Reason.REVOKED)),
                events(CosmeticUnequippedEvent.class));
        assertEquals(Status.NOT_OWNED, actions.revoke(PLAYER, "flame").join().result().status());
    }

    @Test
    void everythingComesOffAtOnce() {
        CosmeticActions actions = actions();
        actions.unlock(PLAYER, "Leon", "flame", CosmeticSource.STAFF).join();
        actions.unlock(PLAYER, "Leon", "crown", CosmeticSource.STAFF).join();
        actions.equip(PLAYER, "flame").join();
        actions.equip(PLAYER, "crown").join();

        assertEquals(Status.DONE, actions.unequipAll(PLAYER).join().result().status());
        assertTrue(actions.state(PLAYER).join().equipped().isEmpty());
        assertEquals(Status.NOT_EQUIPPED, actions.unequipAll(PLAYER).join().result().status());
    }

    @Test
    void unlockingTwiceOnlyFiresOnce() {
        CosmeticActions actions = actions();

        assertEquals(Status.DONE, actions.unlock(PLAYER, "Leon", "gift", CosmeticSource.REWARD).join().result()
                .status());
        assertEquals(Status.ALREADY_OWNED, actions.unlock(PLAYER, "Leon", "gift", CosmeticSource.REWARD).join()
                .result().status());
        assertEquals(Status.UNKNOWN, actions.unlock(PLAYER, "Leon", "nothing", CosmeticSource.REWARD).join()
                .result().status());
        assertEquals(1, events(CosmeticUnlockedEvent.class).size());
        assertEquals(CosmeticSource.REWARD, events(CosmeticUnlockedEvent.class).getFirst().source());
    }
}
