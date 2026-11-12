package net.prismarin.shared.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.prismarin.api.admin.Broadcast;
import net.prismarin.api.admin.Delivery;
import net.prismarin.api.admin.StaffAlert;
import net.prismarin.api.admin.StaffChat;
import net.prismarin.api.moderation.AltAccount;
import net.prismarin.api.moderation.AltService;
import net.prismarin.shared.admin.AuditLog;
import net.prismarin.shared.admin.LocalAuditService;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemoteAltsTest {

    @TempDir
    Path directory;

    private RemoteDispatcher dispatcher(Optional<AltService> alts) {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(5_000L), ZoneOffset.UTC);
        RemoteDispatcher.NoticeSink notices = new RemoteDispatcher.NoticeSink() {
            @Override
            public CompletableFuture<Delivery> staff(StaffAlert alert, String origin) {
                return CompletableFuture.completedFuture(Delivery.DELIVERED);
            }

            @Override
            public CompletableFuture<Delivery> chat(StaffChat message, String origin) {
                return CompletableFuture.completedFuture(Delivery.DELIVERED);
            }

            @Override
            public CompletableFuture<Delivery> broadcast(Broadcast broadcast, String origin) {
                return CompletableFuture.completedFuture(Delivery.DELIVERED);
            }
        };
        return new RemoteDispatcher(new LocalAuditService(new AuditLog(new JsonFileDocumentStore(directory),
                Runnable::run, clock)), Optional::empty, notices, Optional::empty, Optional::empty, () -> alts);
    }

    @Test
    void backendsAskTheProxyForAlts() {
        UUID player = UUID.randomUUID();
        AltAccount alt = new AltAccount(UUID.randomUUID(), "Alex", 1L, 2L, 1, true, false);
        AltService proxy = asked -> CompletableFuture.completedFuture(asked.equals(player) ? List.of(alt) : List.of());
        RemoteAlts remote = new RemoteAlts(new RemoteRoundTripTest.Loopback(dispatcher(Optional.of(proxy))));
        assertEquals(List.of(alt), remote.alts(player).join());
        assertEquals(List.of(alt), remote.bannedAlts(player).join());
        assertTrue(remote.related(player, alt.uuid()).join());
        assertEquals(List.of(), remote.alts(UUID.randomUUID()).join());
    }

    @Test
    void theCallFailsWhenTheProxyHasNoAltDetection() {
        RemoteAlts remote = new RemoteAlts(new RemoteRoundTripTest.Loopback(dispatcher(Optional.empty())));
        CompletionException error = assertThrows(CompletionException.class,
                () -> remote.alts(UUID.randomUUID()).join());
        assertInstanceOf(RemoteCallException.class, error.getCause());
        assertEquals("alt detection is off on the proxy", error.getCause().getMessage());
        assertEquals("alts-find", NetworkCalls.Action.ALTS_FIND.id());
    }
}
