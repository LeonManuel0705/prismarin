package net.prismarin.core.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.api.Prismarin;
import net.prismarin.api.brand.Branding;
import net.prismarin.api.player.NetworkPlayer;
import net.prismarin.api.player.PlayerAttribute;
import net.prismarin.api.player.PlayerAttributes;
import net.prismarin.api.player.PlayerService;
import net.prismarin.api.player.RankDisplay;
import net.prismarin.api.player.ViewerRules;
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

class PlayerPlaceholdersTest {

    private static final RankDisplay FAKE = new RankDisplay("Player", Component.text("Player", NamedTextColor.GRAY),
            Component.empty(), NamedTextColor.GRAY, null, 0);
    private static final RankDisplay ADMIN = new RankDisplay("Admin", Component.text("Admin", NamedTextColor.RED),
            Component.text("!"), NamedTextColor.RED, null, 100);

    @TempDir
    Path directory;

    private final UUID nicked = UUID.randomUUID();
    private final UUID plain = UUID.randomUUID();
    private final UUID ghost = UUID.randomUUID();
    private ExecutorService executor;
    private Prismarin prismarin;
    private PrismarinPlaceholders placeholders;
    private StubPlayers players;

    private final ServerDirectory servers = new ServerDirectory() {
        @Override
        public List<NetworkServer> servers() {
            return List.of(new NetworkServer("bw-1", "BedWars 1", "127.0.0.1", 1, "bedwars", Map.of()));
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
        ProfileServiceImpl profiles = new ProfileServiceImpl(new JsonFileProfileRepository(directory), "de",
                executor, events);
        LocalMessageBus bus = new LocalMessageBus("bw-1");
        NetworkStatusService status = new NetworkStatusService(servers, bus, executor);
        prismarin = new SimplePrismarin("bw-1", Branding.DEFAULT, profiles, new DisabledEconomyService(),
                new YamlLocaleService(directory, "de"), bus, servers, status, new SimpleGameStateService(),
                new SelectingConnector(servers, status, (uuid, server) -> CompletableFuture.completedFuture(true)),
                null, events, Set.of());
        players = new StubPlayers(Map.of(
                nicked, new NetworkPlayer(nicked, "Leon", "SilentFox", Locale.GERMAN, "bw-1", "arena", true, false,
                        FAKE, ADMIN, 4, 0L, NetworkPlayer.Source.LOCAL),
                plain, new NetworkPlayer(plain, "Tim", "", Locale.forLanguageTag("fr"), "bw-1", "lobby", true, false,
                        FAKE, FAKE, 1, 0L, NetworkPlayer.Source.LOCAL),
                ghost, new NetworkPlayer(ghost, "Ghost", "", Locale.ENGLISH, "bw-1", "secret", true, true, ADMIN,
                        ADMIN, 9, 0L, NetworkPlayer.Source.LOCAL)));
        placeholders = new PrismarinPlaceholders(prismarin, null, players,
                PlainTextComponentSerializer.plainText()::serialize);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void localeLanguageWorldAndServerName() {
        assertEquals("de", placeholders.resolve(nicked, "locale"));
        assertEquals("Deutsch", placeholders.resolve(nicked, "language"));
        assertEquals("Français", placeholders.resolve(plain, "language"));
        assertEquals("arena", placeholders.resolve(nicked, "world"));
        assertEquals("BedWars 1", placeholders.resolve(null, "server_name"));
        assertEquals("true", placeholders.resolve(ghost, "vanished"));
        assertEquals("", placeholders.resolve(null, "world"));
        assertEquals("", placeholders.resolve(null, "locale"));
    }

    @Test
    void rankPartsShowTheDisguiseAndTheRealRankSeparately() {
        assertEquals("Player", placeholders.resolve(nicked, "rank_label"));
        assertEquals("Player", placeholders.resolve(nicked, "rank_prefix"));
        assertEquals("§7", placeholders.resolve(nicked, "rank_color"));
        assertEquals("0", placeholders.resolve(nicked, "rank_weight"));
        assertEquals("Admin", placeholders.resolve(nicked, "realrank_label"));
        assertEquals("100", placeholders.resolve(nicked, "realrank_weight"));
        assertEquals("!", placeholders.resolve(nicked, "realrank_suffix"));
        assertEquals("", placeholders.resolve(UUID.randomUUID(), "rank_label"));
        assertEquals("", placeholders.resolve(UUID.randomUUID(), "rank_color"));
        assertNull(placeholders.resolve(nicked, "rank_unknown"));
    }

    @Test
    void relationalRanksRevealOnlyToStaffAndThePlayerHimself() {
        assertEquals("Player", placeholders.resolveRelational(plain, false, nicked, "rank_label"));
        assertEquals("Admin", placeholders.resolveRelational(plain, true, nicked, "rank_label"));
        assertEquals("Admin", placeholders.resolveRelational(nicked, false, nicked, "rank_label"));
        assertEquals("false", placeholders.resolveRelational(plain, false, ghost, "visible"));
        assertEquals("", placeholders.resolveRelational(plain, false, ghost, "world"));
        assertEquals("secret", placeholders.resolveRelational(ghost, false, ghost, "world"));
        assertEquals("SilentFox", placeholders.resolveRelational(plain, false, nicked, "nick"));
        assertNull(placeholders.resolveRelational(plain, false, nicked, "unknown"));
    }

    @Test
    void attributesOfOtherPluginsBecomePlaceholders() {
        players.attributes().register(PlayerAttribute.of("bedwars_wins", player -> String.valueOf(player.level())));
        players.attributes().register(new PlayerAttribute() {
            @Override
            public String key() {
                return "seen_by";
            }

            @Override
            public Optional<String> value(UUID viewer, NetworkPlayer target) {
                return Optional.of(viewer == null ? "nobody" : players.displayName(viewer, target));
            }
        });
        assertEquals("4", placeholders.resolve(nicked, "data_bedwars_wins"));
        assertEquals("", placeholders.resolve(UUID.randomUUID(), "data_bedwars_wins"));
        assertNull(placeholders.resolve(nicked, "data_missing"));
        assertEquals("nobody", placeholders.resolve(nicked, "data_seen_by"));
        assertEquals("SilentFox", placeholders.resolveRelational(plain, false, nicked, "data_seen_by"));
        assertEquals("Leon", placeholders.resolveRelational(nicked, false, nicked, "data_seen_by"));
    }

    @Test
    void withoutPlayerServiceTheOldPlaceholdersStillWork() {
        PrismarinPlaceholders plainPlaceholders = new PrismarinPlaceholders(prismarin);
        assertNull(plainPlaceholders.resolve(nicked, "rank_label"));
        assertEquals("", plainPlaceholders.resolve(nicked, "world"));
    }

    private static final class StubPlayers implements PlayerService {

        private final Map<UUID, NetworkPlayer> online;
        private final PlayerAttributes attributes = new PlayerAttributes();

        StubPlayers(Map<UUID, NetworkPlayer> online) {
            this.online = online;
        }

        @Override
        public Optional<NetworkPlayer> online(UUID player) {
            return Optional.ofNullable(online.get(player));
        }

        @Override
        public Collection<NetworkPlayer> onlinePlayers() {
            return online.values();
        }

        @Override
        public CompletableFuture<Optional<NetworkPlayer>> find(UUID player) {
            return CompletableFuture.completedFuture(online(player));
        }

        @Override
        public CompletableFuture<Optional<NetworkPlayer>> findByRealName(String realName) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public Locale locale(UUID player) {
            return online(player).map(NetworkPlayer::locale).orElse(Locale.GERMAN);
        }

        @Override
        public boolean revealsRealName(UUID viewer, UUID target) {
            return ViewerRules.revealsRealName(viewer, target, false);
        }

        @Override
        public boolean canSee(UUID viewer, NetworkPlayer target) {
            return ViewerRules.canSee(viewer, target, false);
        }

        @Override
        public String displayName(UUID viewer, UUID target) {
            return online(target).map(found -> displayName(viewer, found)).orElse("");
        }

        @Override
        public RankDisplay rank(UUID viewer, UUID target) {
            return online(target).map(found -> rank(viewer, found)).orElse(RankDisplay.NONE);
        }

        @Override
        public Optional<NetworkPlayer> resolveOnline(UUID viewer, String typed) {
            return ViewerRules.resolve(online.values(), typed, target -> revealsRealName(viewer, target.uuid()),
                    target -> canSee(viewer, target));
        }

        @Override
        public CompletableFuture<Optional<NetworkPlayer>> resolve(UUID viewer, String typed) {
            return CompletableFuture.completedFuture(resolveOnline(viewer, typed));
        }

        @Override
        public List<String> suggestions(UUID viewer, String prefix) {
            return List.of();
        }

        @Override
        public PlayerAttributes attributes() {
            return attributes;
        }
    }
}
