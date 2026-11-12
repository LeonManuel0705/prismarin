package net.prismarin.velocity.vote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.proxy.Player;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.prismarin.api.reward.Vote;
import net.prismarin.api.reward.VoteReceivedEvent;
import net.prismarin.shared.event.SimpleEventBus;
import net.prismarin.shared.profile.ProfileServiceImpl;
import net.prismarin.shared.reward.VoteLedger;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import net.prismarin.shared.storage.JsonFileProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProxyVoteServiceTest {

    private static final UUID LEON = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir
    Path directory;

    private final SimpleEventBus events = new SimpleEventBus();
    private final List<Vote> announced = new ArrayList<>();
    private final Map<String, Player> online = new HashMap<>();
    private ExecutorService executor;
    private ProxyVoteService votes;
    private ProfileServiceImpl profiles;
    private MovableClock clock;

    private static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-11-12T10:00:00Z");

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
            return now;
        }
    }

    @BeforeEach
    void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        clock = new MovableClock();
        JsonFileDocumentStore documents = new JsonFileDocumentStore(directory);
        profiles = new ProfileServiceImpl(new JsonFileProfileRepository(directory), "de", executor, events, documents);
        VoteSettings settings = VoteSettings.defaults();
        VoteLedger ledger = new VoteLedger(documents, clock, settings.clock(), settings.graceDays(),
                settings.streaks());
        votes = new ProxyVoteService(settings, ledger, profiles, new ProxyVoteService.OnlinePlayers() {
            @Override
            public Optional<Player> byName(String name) {
                return Optional.ofNullable(online.get(name.toLowerCase(Locale.ROOT)));
            }

            @Override
            public Optional<Player> byUuid(UUID uuid) {
                return online.values().stream().filter(player -> player.getUniqueId().equals(uuid)).findFirst();
            }
        }, events, executor, clock, announced::add);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    private void join(UUID uuid, String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getUsername()).thenReturn(name);
        online.put(name.toLowerCase(Locale.ROOT), player);
    }

    @Test
    void aVoteOfAnOnlinePlayerIsQueuedAnnouncedAndClaimedOnce() {
        join(LEON, "Leon");
        List<VoteReceivedEvent> received = new ArrayList<>();
        events.subscribe(VoteReceivedEvent.class, received::add);

        Vote vote = votes.receive("leon", "example.com", "1.2.3.4").join();

        assertEquals(LEON, vote.player());
        assertEquals("Leon", vote.name());
        assertEquals(1, vote.streak());
        assertEquals(Map.of("coins", 100L), vote.reward().currencies());
        assertEquals(List.of(vote), announced);
        assertEquals(List.of(new VoteReceivedEvent(vote)), received);
        assertEquals(1, votes.stats(LEON).join().pending());
        assertEquals(List.of(vote), votes.claim(LEON, "Leon").join());
        assertTrue(votes.claim(LEON, "Leon").join().isEmpty());
        assertEquals(1L, votes.stats(LEON).join().total());
    }

    @Test
    void votesForOfflinePlayersWaitForTheirNextJoin() {
        profiles.load(LEON, "Leon").join();
        profiles.unload(LEON).join();

        Vote vote = votes.vote("Leon", "example.com").join();

        assertEquals(LEON, vote.player());
        assertEquals(1, votes.pending(LEON).join().size());
        assertEquals(1, votes.claim(LEON, "Leon").join().size());
    }

    @Test
    void votesForUnknownNamesAreKeptByName() {
        Vote vote = votes.vote("NewPlayer", "example.com").join();

        assertFalse(vote.known());
        assertEquals(0, vote.streak());
        UUID newcomer = UUID.randomUUID();
        assertEquals(1, votes.claim(newcomer, "NewPlayer").join().size());
    }

    @Test
    void theStreakGrowsDayByDayAndPaysTheMilestone() {
        join(LEON, "Leon");
        Vote last = null;
        for (int day = 0; day < 7; day++) {
            last = votes.receive("Leon", "example.com", "").join();
            clock.now = clock.now.plus(Duration.ofDays(1));
        }

        assertEquals(7, last.streak());
        assertEquals(Map.of("coins", 100L, "gems", 5L), last.reward().currencies());
        assertEquals(7, votes.stats(LEON).join().bestStreak());
    }

    @Test
    void invalidNamesAreRefused() {
        CompletionException error = assertThrows(CompletionException.class,
                () -> votes.receive("two words", "x", "").join());
        assertTrue(error.getCause() instanceof IllegalArgumentException);
        assertThrows(CompletionException.class, () -> votes.vote(UUID.randomUUID(), "x").join());
    }

    @Test
    void longServiceNamesAreCut() {
        assertEquals(ProxyVoteService.MAX_SERVICE_LENGTH, ProxyVoteService.service("x".repeat(100)).length());
        assertEquals("", ProxyVoteService.service(null));
        assertNull(votes.settings().site("missing").orElse(null));
    }
}
