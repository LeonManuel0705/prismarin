package net.prismarin.shared.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.prismarin.api.moderation.AltAccount;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.shared.event.SimpleEventBus;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AltStoreTest {

    private static final long DAY = Duration.ofDays(1).toMillis();
    private static final String HOME = "a".repeat(64);
    private static final String SCHOOL = "b".repeat(64);
    private static final String CAFE = "c".repeat(64);

    @TempDir
    Path directory;

    private final AtomicLong now = new AtomicLong(10 * DAY);
    private final UUID main = UUID.randomUUID();
    private final UUID alt = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private JsonFileDocumentStore documents;
    private Clock clock;

    @BeforeEach
    void setUp() {
        documents = new JsonFileDocumentStore(directory);
        clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(now.get());
            }
        };
    }

    private AltStore store(int maxAddresses, int sharedLimit, long retention) {
        return new AltStore(documents, clock, maxAddresses, sharedLimit, retention);
    }

    @Test
    void accountsOnTheSameAddressAreAlts() {
        AltStore store = store(10, 10, 90 * DAY);
        store.record(main, "Steve", HOME);
        now.addAndGet(1_000L);
        store.record(alt, "Alex", HOME);
        store.record(stranger, "Bob", CAFE);
        List<AltStore.Link> alts = store.alts(main);
        assertEquals(1, alts.size());
        assertEquals(alt, alts.getFirst().uuid());
        assertEquals("Alex", alts.getFirst().name());
        assertEquals(1, alts.getFirst().sharedAddresses());
        assertEquals(main, store.alts(alt).getFirst().uuid());
        assertEquals(List.of(), store.alts(stranger));
        assertEquals(List.of(), store.alts(UUID.randomUUID()));
    }

    @Test
    void everySharedAddressCountsAndNamesFollowTheLatestLogin() {
        AltStore store = store(10, 10, 90 * DAY);
        store.record(main, "Steve", HOME);
        store.record(main, "Steve", SCHOOL);
        store.record(alt, "Alex", HOME);
        store.record(alt, "Alex", SCHOOL);
        now.addAndGet(DAY);
        store.record(alt, "AlexRenamed", CAFE);
        AltStore.Link link = store.alts(main).getFirst();
        assertEquals(2, link.sharedAddresses());
        assertEquals("AlexRenamed", link.name());
        assertEquals(now.get(), link.lastSeen());
    }

    @Test
    void onlyTheNewestAddressesOfAPlayerAreKept() {
        AltStore store = store(2, 10, 90 * DAY);
        store.record(alt, "Alex", HOME);
        store.record(main, "Steve", HOME);
        now.addAndGet(1_000L);
        store.record(main, "Steve", SCHOOL);
        now.addAndGet(1_000L);
        store.record(main, "Steve", CAFE);
        assertEquals(List.of(), store.alts(main));
    }

    @Test
    void oldLoginsExpire() {
        AltStore store = store(10, 10, 30 * DAY);
        store.record(main, "Steve", HOME);
        store.record(alt, "Alex", HOME);
        assertEquals(1, store.alts(main).size());
        now.addAndGet(31 * DAY);
        assertEquals(List.of(), store.alts(main));
        assertEquals(3, store.prune());
        assertTrue(documents.findAll(AltStore.ACCOUNTS).isEmpty());
        assertTrue(documents.findAll(AltStore.ADDRESSES).isEmpty());
    }

    @Test
    void sharedAddressesLinkNobody() {
        AltStore store = store(10, 2, 90 * DAY);
        store.record(main, "Steve", HOME);
        store.record(alt, "Alex", HOME);
        assertEquals(1, store.alts(main).size());
        store.record(stranger, "Bob", HOME);
        assertEquals(List.of(), store.alts(main));
        store.record(main, "Steve", SCHOOL);
        store.record(alt, "Alex", SCHOOL);
        assertEquals(1, store.alts(main).getFirst().sharedAddresses());
    }

    @Test
    void nothingIsStoredWithoutAnAddress() {
        AltStore store = store(10, 10, 90 * DAY);
        store.record(main, "Steve", "");
        store.record(main, "Steve", null);
        assertTrue(documents.findAll(AltStore.ACCOUNTS).isEmpty());
    }

    @Test
    void theServiceMarksBansAndMutes() {
        AltStore store = store(10, 10, 90 * DAY);
        DocumentPunishmentService punishments = new DocumentPunishmentService(documents, Runnable::run,
                new SimpleEventBus(), clock);
        DocumentAltService service = new DocumentAltService(store, punishments, Runnable::run);
        service.record(main, "Steve", HOME).join();
        service.record(alt, "Alex", HOME).join();
        service.record(stranger, "Bob", HOME).join();
        punishments.punish(alt, "Alex", PunishmentType.BAN, "Hacking", "Leon", null).join();
        punishments.punish(stranger, "Bob", PunishmentType.MUTE, "Spam", "Leon", Duration.ofHours(1)).join();
        List<AltAccount> alts = service.alts(main).join();
        assertEquals(2, alts.size());
        AltAccount banned = alts.stream().filter(account -> account.uuid().equals(alt)).findFirst().orElseThrow();
        assertTrue(banned.banned());
        assertFalse(banned.muted());
        AltAccount muted = alts.stream().filter(account -> account.uuid().equals(stranger)).findFirst()
                .orElseThrow();
        assertTrue(muted.muted());
        assertTrue(muted.punished());
        assertEquals(List.of(alt), service.bannedAlts(main).join().stream().map(AltAccount::uuid).toList());
        assertTrue(service.related(main, alt).join());
        assertFalse(service.related(main, UUID.randomUUID()).join());
        assertEquals(0, service.prune().join());
    }
}
