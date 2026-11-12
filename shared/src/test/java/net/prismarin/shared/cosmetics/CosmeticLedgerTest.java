package net.prismarin.shared.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticHat;
import net.prismarin.api.cosmetics.CosmeticSlot;
import net.prismarin.api.cosmetics.CosmeticSource;
import net.prismarin.api.cosmetics.PlayerCosmetics;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import net.prismarin.shared.storage.SqlDatabase;
import net.prismarin.shared.storage.SqlDocumentStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CosmeticLedgerTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path directory;

    private CosmeticLedger ledger() {
        return new CosmeticLedger(new JsonFileDocumentStore(directory), CLOCK);
    }

    @Test
    void aNewPlayerOwnsNothing() {
        assertEquals(PlayerCosmetics.none(PLAYER), ledger().state(PLAYER));
    }

    @Test
    void grantingTwiceKeepsTheFirstSource() {
        CosmeticLedger ledger = ledger();

        assertTrue(ledger.grant(PLAYER, "Leon", "flame-trail", CosmeticSource.PURCHASE).changed());
        CosmeticLedger.Change again = ledger.grant(PLAYER, "Leon", "flame-trail", CosmeticSource.REWARD);

        assertFalse(again.changed());
        assertEquals(CosmeticSource.PURCHASE, again.state().owned().get("flame-trail").source());
        assertEquals(CLOCK.millis(), again.state().owned().get("flame-trail").at());
    }

    @Test
    void oneCosmeticPerSlotAndEquippingReturnsTheOldOne() {
        CosmeticLedger ledger = ledger();
        Cosmetic flame = CosmeticCatalogTest.trail("flame-trail");
        Cosmetic hearts = CosmeticCatalogTest.trail("heart-trail");
        Cosmetic crown = Cosmetic.hat("crown", null, CosmeticHat.item("golden_helmet"));

        assertEquals(Optional.empty(), ledger.equip(PLAYER, flame).previousId());
        ledger.equip(PLAYER, crown);
        CosmeticLedger.Change swapped = ledger.equip(PLAYER, hearts);

        assertTrue(swapped.changed());
        assertEquals(Optional.of("flame-trail"), swapped.previousId());
        assertEquals(Map.of(CosmeticSlot.TRAIL, "heart-trail", CosmeticSlot.HAT, "crown"), swapped.state().equipped());
        assertFalse(ledger.equip(PLAYER, hearts).changed());
    }

    @Test
    void revokingTakesTheCosmeticOffToo() {
        CosmeticLedger ledger = ledger();
        Cosmetic flame = CosmeticCatalogTest.trail("flame-trail");
        ledger.grant(PLAYER, "Leon", flame.id(), CosmeticSource.STAFF);
        ledger.equip(PLAYER, flame);

        CosmeticLedger.Change revoked = ledger.revoke(PLAYER, flame.id());

        assertTrue(revoked.changed());
        assertEquals(CosmeticSlot.TRAIL, revoked.slot());
        assertTrue(revoked.state().equipped().isEmpty());
        assertFalse(revoked.state().owns(flame.id()));
        assertFalse(ledger.revoke(PLAYER, flame.id()).changed());
    }

    @Test
    void unequipAndHideOnlyWriteWhenSomethingChanges() {
        CosmeticLedger ledger = ledger();
        ledger.equip(PLAYER, CosmeticCatalogTest.trail("flame-trail"));

        assertEquals("flame-trail", ledger.unequip(PLAYER, CosmeticSlot.TRAIL).previous());
        assertFalse(ledger.unequip(PLAYER, CosmeticSlot.TRAIL).changed());
        assertTrue(ledger.hide(PLAYER, true).state().hidesOthers());
        assertFalse(ledger.hide(PLAYER, true).changed());
        assertTrue(ledger.state(PLAYER).hidesOthers());
    }

    @Test
    void brokenDocumentsCountAsEmpty() {
        JsonFileDocumentStore store = new JsonFileDocumentStore(directory);
        store.save(CosmeticLedger.COLLECTION, PLAYER.toString(), "{not json");
        CosmeticLedger ledger = new CosmeticLedger(store, CLOCK);

        assertEquals(PlayerCosmetics.none(PLAYER), ledger.state(PLAYER));
        assertTrue(ledger.grant(PLAYER, "Leon", "flame-trail", CosmeticSource.PLUGIN).changed());
    }

    @Test
    void twoServersGrantTheSameCosmeticOnlyOnce() {
        SqlDatabase lobbyDatabase = SqlDatabase.sqlite(directory.resolve("shared.db"));
        SqlDatabase gameDatabase = SqlDatabase.sqlite(directory.resolve("shared.db"));
        try {
            CosmeticLedger lobby = new CosmeticLedger(new SqlDocumentStore(lobbyDatabase), CLOCK);
            CosmeticLedger game = new CosmeticLedger(new SqlDocumentStore(gameDatabase), CLOCK);
            AtomicInteger granted = new AtomicInteger();
            ExecutorService executor = Executors.newFixedThreadPool(8);
            try {
                CompletableFuture<?>[] grants = new CompletableFuture<?>[16];
                for (int index = 0; index < grants.length; index++) {
                    CosmeticLedger server = index % 2 == 0 ? lobby : game;
                    grants[index] = CompletableFuture.runAsync(() -> {
                        if (server.grant(PLAYER, "Leon", "angel-wings", CosmeticSource.PURCHASE).changed()) {
                            granted.incrementAndGet();
                        }
                    }, executor);
                }
                CompletableFuture.allOf(grants).join();
            } finally {
                executor.shutdown();
            }

            assertEquals(1, granted.get());
            assertTrue(game.state(PLAYER).owns("angel-wings"));
        } finally {
            lobbyDatabase.close();
            gameDatabase.close();
        }
    }
}
