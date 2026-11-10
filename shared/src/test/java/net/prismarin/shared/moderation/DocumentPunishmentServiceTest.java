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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.PunishmentIssuedEvent;
import net.prismarin.api.moderation.PunishmentRequest;
import net.prismarin.api.moderation.PunishmentRevokedEvent;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.shared.event.SimpleEventBus;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentPunishmentServiceTest {

    @TempDir
    Path directory;

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final UUID player = UUID.randomUUID();
    private final List<PrismarinEvent> events = new ArrayList<>();
    private DocumentPunishmentService service;
    private JsonFileDocumentStore store;

    @BeforeEach
    void setUp() {
        SimpleEventBus bus = new SimpleEventBus();
        bus.subscribe(PrismarinEvent.class, events::add);
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
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
        store = new JsonFileDocumentStore(directory);
        service = new DocumentPunishmentService(store, Runnable::run, bus, clock);
    }

    @Test
    void temporaryBanExpires() {
        service.punish(player, "Steve", PunishmentType.BAN, "Hacking", "Leon", Duration.ofHours(1)).join();
        assertTrue(service.active(player, PunishmentType.BAN).join().isPresent());
        now.addAndGet(Duration.ofHours(2).toMillis());
        assertTrue(service.active(player, PunishmentType.BAN).join().isEmpty());
    }

    @Test
    void permanentMuteStaysUntilRevoked() {
        Punishment mute = service.punish(player, "Steve", PunishmentType.MUTE, "Spam", "Leon", null).join();
        assertTrue(mute.permanent());
        now.addAndGet(Duration.ofDays(400).toMillis());
        assertTrue(service.active(player, PunishmentType.MUTE).join().isPresent());
        assertTrue(service.revoke(player, PunishmentType.MUTE, "Leon").join().isPresent());
        assertTrue(service.active(player, PunishmentType.MUTE).join().isEmpty());
        assertTrue(events.stream().anyMatch(PunishmentIssuedEvent.class::isInstance));
        assertTrue(events.stream().anyMatch(PunishmentRevokedEvent.class::isInstance));
    }

    @Test
    void newBanReplacesTheOldOne() {
        service.punish(player, "Steve", PunishmentType.BAN, "first", "Leon", Duration.ofDays(1)).join();
        now.addAndGet(1000L);
        service.punish(player, "Steve", PunishmentType.BAN, "second", "Leon", null).join();
        assertEquals("second", service.active(player, PunishmentType.BAN).join().orElseThrow().reason());
        assertEquals(1L, service.history(player).join().stream().filter(entry -> entry.inForce(now.get())).count());
    }

    @Test
    void kicksAndWarningsAreHistoryOnly() {
        Punishment kick = service.punish(player, "Steve", PunishmentType.KICK, "afk", "Leon", null).join();
        service.punish(player, "Steve", PunishmentType.WARN, "language", "Leon", null).join();
        assertFalse(kick.active());
        assertEquals(2, service.history(player).join().size());
        assertTrue(service.revoke(player, PunishmentType.BAN, "Leon").join().isEmpty());
    }

    @Test
    void historyIsSeparatedPerPlayer() {
        service.punish(player, "Steve", PunishmentType.WARN, "a", "Leon", null).join();
        service.punish(UUID.randomUUID(), "Alex", PunishmentType.WARN, "b", "Leon", null).join();
        assertEquals(1, service.history(player).join().size());
    }

    @Test
    void readsPunishmentsStoredBeforeTemplatesExisted() {
        store.save(DocumentPunishmentService.COLLECTION, "old1", "{\"id\":\"old1\",\"target\":\"" + player
                + "\",\"targetName\":\"Steve\",\"type\":\"BAN\",\"reason\":\"Hacking\",\"actor\":\"Leon\","
                + "\"createdAt\":900000,\"expiresAt\":0,\"active\":true,\"revokedBy\":\"\",\"revokedAt\":0}");
        Punishment old = service.active(player, PunishmentType.BAN).join().orElseThrow();
        assertEquals("Hacking", old.reason());
        assertEquals("", old.template());
        assertFalse(old.addressBound());
        assertFalse(old.lifted());
        assertTrue(old.permanent());
    }

    @Test
    void replacedPunishmentsPointToTheirSuccessor() {
        Punishment first = service.punish(player, "Steve", PunishmentType.MUTE, "a", "Leon", Duration.ofHours(1))
                .join();
        Punishment second = service.punish(PunishmentRequest.of(player, "Steve", PunishmentType.MUTE, "b", "Leon",
                Duration.ofHours(6)).withTemplate("chat", "caps")).join();
        Punishment stored = service.history(player).join().stream()
                .filter(entry -> entry.id().equals(first.id())).findFirst().orElseThrow();
        assertEquals(second.id(), stored.replacedBy());
        assertFalse(stored.lifted());
        assertEquals("chat", second.template());
        assertEquals("caps", second.note());
    }

    @Test
    void liftedPunishmentsKeepTheReason() {
        service.punish(player, "Steve", PunishmentType.BAN, "a", "Leon", null).join();
        Punishment lifted = service.revoke(player, PunishmentType.BAN, "Mod", "appeal accepted").join().orElseThrow();
        assertTrue(lifted.lifted());
        assertEquals("appeal accepted", lifted.revokeReason());
        assertEquals("Mod", lifted.revokedBy());
    }

    @Test
    void addressBansAreFoundByTheirHash() {
        service.punish(PunishmentRequest.of(player, "Steve", PunishmentType.BAN, "alts", "Leon", Duration.ofDays(1))
                .withAddress("abc123")).join();
        service.punish(PunishmentRequest.of(UUID.randomUUID(), "Alex", PunishmentType.MUTE, "x", "Leon", null)
                .withAddress("abc123")).join();
        assertEquals(player, service.activeForAddress("abc123").join().orElseThrow().target());
        assertTrue(service.activeForAddress("other").join().isEmpty());
        assertTrue(service.activeForAddress("").join().isEmpty());
        now.addAndGet(Duration.ofDays(2).toMillis());
        assertTrue(service.activeForAddress("abc123").join().isEmpty());
    }
}
