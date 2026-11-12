package net.prismarin.shared.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.prismarin.api.admin.Broadcast;
import net.prismarin.api.admin.Delivery;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.admin.StaffAlert;
import net.prismarin.api.admin.StaffChat;
import net.prismarin.api.reward.Reward;
import net.prismarin.api.reward.Vote;
import net.prismarin.api.reward.VoteReceivedEvent;
import net.prismarin.api.reward.VoteService;
import net.prismarin.api.reward.VoteSite;
import net.prismarin.api.reward.VoteStats;
import net.prismarin.shared.admin.AuditLog;
import net.prismarin.shared.admin.LocalAuditService;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemoteVotesTest {

    private static final UUID LEON = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir
    Path directory;

    private RemoteDispatcher dispatcher(Optional<VoteService> votes) {
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
                Runnable::run, clock)), Optional::empty, notices, Optional::empty, Optional::empty, Optional::empty,
                Optional::empty, () -> votes, Optional::empty, Optional::empty);
    }

    private static Vote queued(String id) {
        Reward reward = Reward.currency("coins", 100L).plus(Reward.message(NoticeText.of(Map.of("en", "Thanks"))));
        return new Vote(id, LEON, "Leon", "example.com", "", 1_000L, 2, reward);
    }

    private static final class Proxy implements VoteService {

        final List<Vote> queue = new ArrayList<>();
        final List<String> cast = new ArrayList<>();

        @Override
        public CompletableFuture<List<VoteSite>> sites() {
            return CompletableFuture.completedFuture(List.of(new VoteSite("eu", "Server EU", "https://x.eu", "x.eu")));
        }

        @Override
        public CompletableFuture<Vote> vote(String name, String service) {
            cast.add(name + "@" + service);
            return CompletableFuture.completedFuture(new Vote("n", null, name, service, "", 1L, 0, Reward.EMPTY));
        }

        @Override
        public CompletableFuture<Vote> vote(UUID player, String service) {
            cast.add(player + "@" + service);
            return CompletableFuture.completedFuture(queued("u"));
        }

        @Override
        public CompletableFuture<VoteStats> stats(UUID player) {
            return CompletableFuture.completedFuture(new VoteStats(player, 12L, 3, 5, 99L, queue.size()));
        }

        @Override
        public CompletableFuture<List<Vote>> pending(UUID player) {
            return CompletableFuture.completedFuture(List.copyOf(queue));
        }

        @Override
        public synchronized CompletableFuture<List<Vote>> claim(UUID player, String name, int limit) {
            List<Vote> batch = new ArrayList<>(queue.subList(0, Math.min(limit, queue.size())));
            queue.subList(0, batch.size()).clear();
            return CompletableFuture.completedFuture(batch);
        }
    }

    @Test
    void backendsClaimQueuedVotesInBatches() {
        Proxy proxy = new Proxy();
        for (int index = 0; index < 23; index++) {
            proxy.queue.add(queued("v" + index));
        }
        RemoteRoundTripTest.Loopback link = new RemoteRoundTripTest.Loopback(dispatcher(Optional.of(proxy)));
        RemoteVotes remote = new RemoteVotes(link);

        List<Vote> claimed = remote.claim(LEON, "Leon").join();

        assertEquals(23, claimed.size());
        assertEquals(queued("v0"), claimed.getFirst());
        assertEquals(4, link.calls.get());
        assertTrue(proxy.queue.isEmpty());
        assertEquals(List.of(), remote.claim(LEON, "Leon").join());
    }

    @Test
    void aLimitStopsTheClaim() {
        Proxy proxy = new Proxy();
        for (int index = 0; index < 5; index++) {
            proxy.queue.add(queued("v" + index));
        }
        RemoteVotes remote = new RemoteVotes(new RemoteRoundTripTest.Loopback(dispatcher(Optional.of(proxy))));

        assertEquals(3, remote.claim(LEON, "Leon", 3).join().size());
        assertEquals(2, proxy.queue.size());
    }

    @Test
    void votesSitesAndStatsTravel() {
        Proxy proxy = new Proxy();
        RemoteVotes remote = new RemoteVotes(new RemoteRoundTripTest.Loopback(dispatcher(Optional.of(proxy))));

        assertEquals("n", remote.vote("Steve", "custom-list").join().id());
        assertEquals("u", remote.vote(LEON, "custom-list").join().id());
        assertEquals(List.of("Steve@custom-list", LEON + "@custom-list"), proxy.cast);
        assertEquals("Server EU", remote.sites().join().getFirst().name());
        assertEquals(12L, remote.stats(LEON).join().total());
        assertEquals(List.of(), remote.pending(LEON).join());
    }

    @Test
    void theCallFailsWhenTheProxyHasNoVotes() {
        RemoteVotes remote = new RemoteVotes(new RemoteRoundTripTest.Loopback(dispatcher(Optional.empty())));

        CompletionException error = assertThrows(CompletionException.class, () -> remote.stats(LEON).join());
        assertInstanceOf(RemoteCallException.class, error.getCause());
        assertEquals("votes are off on the proxy", error.getCause().getMessage());
        assertThrows(CompletionException.class, () -> remote.vote(" ", "x").join());
    }

    @Test
    void receivedVotesTravelAsEvents() {
        Vote vote = queued("relayed");

        Optional<?> decoded = NetworkEvents.encode(new VoteReceivedEvent(vote)).flatMap(NetworkEvents::decode);

        assertEquals(Optional.of(new VoteReceivedEvent(vote)), decoded);
        assertEquals("vote-claim", NetworkCalls.Action.VOTE_CLAIM.id());
    }
}
