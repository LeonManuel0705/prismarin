package net.prismarin.core.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.prismarin.api.Prismarin;
import net.prismarin.api.brand.Branding;
import net.prismarin.api.nick.NickResult;
import net.prismarin.api.nick.NickService;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.shared.economy.DisabledEconomyService;
import net.prismarin.shared.event.SimpleEventBus;
import net.prismarin.shared.locale.YamlLocaleService;
import net.prismarin.shared.messaging.LocalMessageBus;
import net.prismarin.shared.platform.SimplePrismarin;
import net.prismarin.shared.profile.ProfileServiceImpl;
import net.prismarin.shared.status.NetworkStatusService;
import net.prismarin.shared.status.SelectingConnector;
import net.prismarin.shared.status.SimpleGameStateService;
import net.prismarin.shared.storage.JsonFileProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NickPlaceholdersTest {

    @TempDir
    Path directory;

    private final UUID nicked = UUID.randomUUID();
    private final UUID plain = UUID.randomUUID();
    private final UUID staff = UUID.randomUUID();
    private ExecutorService executor;
    private Prismarin prismarin;

    private final ServerDirectory servers = new ServerDirectory() {
        @Override
        public List<NetworkServer> servers() {
            return List.of();
        }

        @Override
        public Optional<NetworkServer> byName(String name) {
            return Optional.empty();
        }
    };

    @BeforeEach
    void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        SimpleEventBus events = new SimpleEventBus();
        ProfileServiceImpl profiles = new ProfileServiceImpl(new JsonFileProfileRepository(directory), "de",
                executor, events);
        LocalMessageBus bus = new LocalMessageBus("bw-1");
        NetworkStatusService status = new NetworkStatusService(servers, bus, executor);
        prismarin = new SimplePrismarin("bw-1", Branding.DEFAULT, profiles, new DisabledEconomyService(),
                new YamlLocaleService(directory, "de"), bus, servers, status, new SimpleGameStateService(),
                new SelectingConnector(servers, status, (uuid, server) -> CompletableFuture.completedFuture(true)),
                null, events, Set.of());
        profiles.load(nicked, "Leon").join();
        profiles.load(plain, "Tim").join();
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void nickPlaceholdersShowTheNickForNickedPlayers() {
        PrismarinPlaceholders placeholders = new PrismarinPlaceholders(prismarin, new StubNicks());
        assertEquals("SilentFox", placeholders.resolve(nicked, "nick"));
        assertEquals("Leon", placeholders.resolve(nicked, "realname"));
        assertEquals("true", placeholders.resolve(nicked, "nicked"));
        assertEquals("Tim", placeholders.resolve(plain, "nick"));
        assertEquals("Tim", placeholders.resolve(plain, "realname"));
        assertEquals("false", placeholders.resolve(plain, "nicked"));
        assertEquals("", placeholders.resolve(null, "nick"));
        assertEquals("false", placeholders.resolve(null, "nicked"));
    }

    @Test
    void withoutNickServiceTheRealNameIsShown() {
        PrismarinPlaceholders placeholders = new PrismarinPlaceholders(prismarin);
        assertEquals("Leon", placeholders.resolve(nicked, "nick"));
        assertEquals("Leon", placeholders.resolve(nicked, "realname"));
        assertEquals("false", placeholders.resolve(nicked, "nicked"));
    }

    @Test
    void relationalRealNameOnlyRevealsToStaffAndThePlayerHimself() {
        PrismarinPlaceholders placeholders = new PrismarinPlaceholders(prismarin, new StubNicks());
        assertEquals("SilentFox", placeholders.resolveRelational(plain, false, nicked, "realname"));
        assertEquals("Leon", placeholders.resolveRelational(staff, true, nicked, "realname"));
        assertEquals("Leon", placeholders.resolveRelational(nicked, false, nicked, "realname"));
        assertEquals("SilentFox", placeholders.resolveRelational(staff, true, nicked, "nick"));
        assertNull(placeholders.resolveRelational(staff, true, nicked, "balance"));
        assertNull(placeholders.resolveRelational(staff, true, null, "realname"));
    }

    private final class StubNicks implements NickService {

        @Override
        public boolean isNicked(UUID player) {
            return nicked.equals(player);
        }

        @Override
        public Optional<String> shownNick(UUID player) {
            return isNicked(player) ? Optional.of("SilentFox") : Optional.empty();
        }

        @Override
        public Optional<String> storedNick(UUID player) {
            return shownNick(player);
        }

        @Override
        public Optional<String> realName(UUID player) {
            return Optional.ofNullable(Map.of(nicked, "Leon", plain, "Tim").get(player));
        }

        @Override
        public Optional<UUID> findByNick(String nick) {
            return "SilentFox".equalsIgnoreCase(nick) ? Optional.of(nicked) : Optional.empty();
        }

        @Override
        public Collection<UUID> nickedPlayers() {
            return List.of(nicked);
        }

        @Override
        public CompletableFuture<Optional<UUID>> lookupNick(String nick) {
            return CompletableFuture.completedFuture(findByNick(nick));
        }

        @Override
        public CompletableFuture<NickResult> nickRandom(UUID player) {
            return CompletableFuture.completedFuture(NickResult.of(NickResult.Status.DISABLED));
        }

        @Override
        public CompletableFuture<NickResult> nick(UUID player, String name) {
            return nickRandom(player);
        }

        @Override
        public CompletableFuture<NickResult> unnick(UUID player) {
            return nickRandom(player);
        }

        @Override
        public boolean autoNick(UUID player) {
            return false;
        }

        @Override
        public CompletableFuture<Void> setAutoNick(UUID player, boolean enabled) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void markInRound(UUID player, boolean inRound) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clearRoundMarker(UUID player) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean inRound(UUID player) {
            return false;
        }

        @Override
        public boolean nicksShownHere() {
            return true;
        }
    }
}
