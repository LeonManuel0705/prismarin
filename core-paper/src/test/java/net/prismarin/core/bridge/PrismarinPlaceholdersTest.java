package net.prismarin.core.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.time.Clock;
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
import net.prismarin.api.economy.Currency;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.shared.economy.DisabledEconomyService;
import net.prismarin.shared.economy.EconomyServiceImpl;
import net.prismarin.shared.event.SimpleEventBus;
import net.prismarin.shared.locale.YamlLocaleService;
import net.prismarin.shared.messaging.LocalMessageBus;
import net.prismarin.shared.platform.SimplePrismarin;
import net.prismarin.shared.profile.ProfileServiceImpl;
import net.prismarin.shared.status.MinecraftPinger;
import net.prismarin.shared.status.NetworkStatusService;
import net.prismarin.shared.status.SelectingConnector;
import net.prismarin.shared.status.SimpleGameStateService;
import net.prismarin.shared.storage.JsonFileProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrismarinPlaceholdersTest {

    @TempDir
    Path directory;

    private final UUID player = UUID.randomUUID();
    private final Currency tokens = Currency.of("tokens");
    private ExecutorService executor;
    private Prismarin prismarin;

    private final ServerDirectory servers = new ServerDirectory() {
        @Override
        public List<NetworkServer> servers() {
            return List.of(new NetworkServer("bw_1", "BW 1", "127.0.0.1", 1, "bedwars", Map.of()),
                    new NetworkServer("bw_2", "BW 2", "127.0.0.1", 2, "bedwars", Map.of()));
        }

        @Override
        public Optional<NetworkServer> byName(String name) {
            return servers().stream().filter(server -> server.name().equalsIgnoreCase(name)).findFirst();
        }
    };

    @BeforeEach
    void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        SimpleEventBus events = new SimpleEventBus();
        JsonFileProfileRepository repository = new JsonFileProfileRepository(directory);
        ProfileServiceImpl profiles = new ProfileServiceImpl(repository, "de", executor, events);
        EconomyServiceImpl economy = new EconomyServiceImpl(repository, executor,
                List.of(Currency.COINS, tokens), Currency.COINS, events);
        LocalMessageBus bus = new LocalMessageBus("lobby");
        NetworkStatusService status = new NetworkStatusService(servers, bus, Runnable::run,
                Clock.systemUTC(), (host, port, timeout) -> new MinecraftPinger.Result(port * 3, 16, "", 1L,
                        port == 1 ? "prismarin:RUNNING:" : ""));
        status.refresh().join();
        prismarin = new SimplePrismarin("lobby", Branding.DEFAULT, profiles, economy,
                new YamlLocaleService(directory, "de"), bus, servers, status, new SimpleGameStateService(),
                new SelectingConnector(servers, status, (uuid, server) -> CompletableFuture.completedFuture(true)),
                null, events, Set.of("economy"));
        profiles.load(player, "Leon").join();
        economy.deposit(player, Currency.COINS, 40L).join();
        economy.deposit(player, tokens, 7L).join();
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void balancesAndProfileValues() {
        PrismarinPlaceholders placeholders = new PrismarinPlaceholders(prismarin);
        assertEquals("40", placeholders.resolve(player, "balance"));
        assertEquals("7", placeholders.resolve(player, "balance_tokens"));
        assertEquals("0", placeholders.resolve(player, "level"));
        assertEquals("PRISMARIN", placeholders.resolve(player, "brand"));
        assertNull(placeholders.resolve(player, "balance_gold"));
    }

    @Test
    void serverAndGroupCounts() {
        PrismarinPlaceholders placeholders = new PrismarinPlaceholders(prismarin);
        assertEquals("3", placeholders.resolve(null, "server_online_bw_1"));
        assertEquals("online", placeholders.resolve(null, "server_status_BW_2"));
        assertEquals("9", placeholders.resolve(null, "group_online_bedwars"));
        assertEquals("9", placeholders.resolve(null, "network_online"));
        assertNull(placeholders.resolve(null, "server_online_nope"));
        assertNull(placeholders.resolve(null, "unknown"));
    }

    @Test
    void disabledEconomyShowsZero() {
        Prismarin disabled = new SimplePrismarin("lobby", Branding.DEFAULT, prismarin.profiles(),
                new DisabledEconomyService(), prismarin.locale(), prismarin.messageBus(), servers,
                prismarin.serverStatus(), prismarin.gameState(), prismarin.connector(), null, prismarin.events(),
                Set.of());
        assertEquals("0", new PrismarinPlaceholders(disabled).resolve(player, "balance"));
    }

    @Test
    void statesAndJoinableCounts() {
        PrismarinPlaceholders placeholders = new PrismarinPlaceholders(prismarin);
        assertEquals("running", placeholders.resolve(null, "server_state_bw_1"));
        assertEquals("unknown", placeholders.resolve(null, "server_state_bw_2"));
        assertEquals("1", placeholders.resolve(null, "group_joinable_bedwars"));
    }
}
