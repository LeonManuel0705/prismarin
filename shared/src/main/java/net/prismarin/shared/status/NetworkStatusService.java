package net.prismarin.shared.status;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.prismarin.api.messaging.MessageBus;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerStatus;
import net.prismarin.api.server.ServerStatusService;

public final class NetworkStatusService implements ServerStatusService {

    public static final String CHANNEL = "prismarin:heartbeat";
    static final Duration HEARTBEAT_TTL = Duration.ofSeconds(20);
    private static final Duration PING_TIMEOUT = Duration.ofSeconds(2);
    private static final Logger LOGGER = Logger.getLogger(NetworkStatusService.class.getName());

    private final ServerDirectory directory;
    private final MessageBus bus;
    private final Executor executor;
    private final Clock clock;
    private final Pinger pinger;
    private final Gson gson = new Gson();
    private final Map<String, Heartbeat> heartbeats = new ConcurrentHashMap<>();
    private final Map<String, Instant> heartbeatReceived = new ConcurrentHashMap<>();
    private final Map<String, ServerStatus> statuses = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface Pinger {
        MinecraftPinger.Result ping(String host, int port, Duration timeout) throws Exception;
    }

    public NetworkStatusService(ServerDirectory directory, MessageBus bus, Executor executor) {
        this(directory, bus, executor, Clock.systemUTC(), MinecraftPinger::ping);
    }

    public NetworkStatusService(ServerDirectory directory, MessageBus bus, Executor executor, Clock clock,
                                Pinger pinger) {
        this.directory = directory;
        this.bus = bus;
        this.executor = executor;
        this.clock = clock;
        this.pinger = pinger;
        bus.subscribe(CHANNEL, message -> {
            try {
                accept(gson.fromJson(message.payloadJson(), Heartbeat.class));
            } catch (JsonParseException e) {
                LOGGER.log(Level.FINE, "Ignoring malformed heartbeat", e);
            }
        });
    }

    public void publish(Heartbeat heartbeat) {
        bus.publish(CHANNEL, gson.toJson(heartbeat));
    }

    void accept(Heartbeat heartbeat) {
        if (heartbeat == null || heartbeat.serverId() == null) {
            return;
        }
        heartbeats.put(heartbeat.serverId().toLowerCase(Locale.ROOT), heartbeat);
        heartbeatReceived.put(heartbeat.serverId().toLowerCase(Locale.ROOT), clock.instant());
    }

    @Override
    public List<ServerStatus> statuses() {
        Instant now = clock.instant();
        return directory.servers().stream()
                .map(server -> statuses.getOrDefault(key(server), ServerStatus.offline(server.name(), now)))
                .toList();
    }

    @Override
    public Optional<ServerStatus> status(String name) {
        return directory.byName(name).map(server -> statuses.getOrDefault(key(server),
                ServerStatus.offline(server.name(), clock.instant())));
    }

    @Override
    public CompletableFuture<List<ServerStatus>> refresh() {
        List<CompletableFuture<Void>> pings = directory.servers().stream()
                .map(server -> CompletableFuture.runAsync(() -> statuses.put(key(server), probe(server)), executor))
                .toList();
        return CompletableFuture.allOf(pings.toArray(CompletableFuture[]::new)).thenApply(unused -> statuses());
    }

    private ServerStatus probe(NetworkServer server) {
        Instant now = clock.instant();
        Optional<Heartbeat> heartbeat = freshHeartbeat(server, now);
        try {
            MinecraftPinger.Result result = pinger.ping(server.host(), server.port(), PING_TIMEOUT);
            return heartbeat
                    .map(beat -> new ServerStatus(server.name(), true, result.players(), result.maxPlayers(),
                            result.latencyMillis(), result.version(), true, beat.tps(), beat.mspt(),
                            beat.usedMemoryMb(), beat.maxMemoryMb(), beat.uptimeSeconds(), now))
                    .orElseGet(() -> new ServerStatus(server.name(), true, result.players(), result.maxPlayers(),
                            result.latencyMillis(), result.version(), false, 0D, 0D, 0L, 0L, 0L, now));
        } catch (Exception e) {
            return heartbeat
                    .map(beat -> new ServerStatus(server.name(), true, beat.players(), beat.maxPlayers(), -1L,
                            beat.version(), true, beat.tps(), beat.mspt(), beat.usedMemoryMb(), beat.maxMemoryMb(),
                            beat.uptimeSeconds(), now))
                    .orElseGet(() -> ServerStatus.offline(server.name(), now));
        }
    }

    private Optional<Heartbeat> freshHeartbeat(NetworkServer server, Instant now) {
        Instant received = heartbeatReceived.get(key(server));
        if (received == null || received.plus(HEARTBEAT_TTL).isBefore(now)) {
            return Optional.empty();
        }
        return Optional.ofNullable(heartbeats.get(key(server)));
    }

    private static String key(NetworkServer server) {
        return server.name().toLowerCase(Locale.ROOT);
    }
}
