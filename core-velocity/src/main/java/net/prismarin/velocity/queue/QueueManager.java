package net.prismarin.velocity.queue;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.queue.QueueEntry;
import net.prismarin.api.queue.QueueInfo;
import net.prismarin.api.queue.QueueJoinEvent;
import net.prismarin.api.queue.QueueJoinedEvent;
import net.prismarin.api.queue.QueueLeftEvent;
import net.prismarin.api.queue.QueuePausedEvent;
import net.prismarin.api.queue.QueuePosition;
import net.prismarin.api.queue.QueueResult;
import net.prismarin.api.queue.QueueSendEvent;
import net.prismarin.api.queue.QueueSentEvent;
import net.prismarin.api.queue.QueueService;
import net.prismarin.api.queue.QueueStatus;
import net.prismarin.api.queue.QueueTarget;
import net.prismarin.api.server.GameState;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerGroup;
import net.prismarin.api.server.ServerStatus;
import net.prismarin.api.social.Party;
import net.prismarin.api.social.PartyService;
import net.prismarin.api.social.SocialApi;
import net.prismarin.shared.status.NetworkStatusService;
import net.prismarin.velocity.admin.ProxyRemote;
import net.prismarin.velocity.locale.ProxyMessages;
import org.slf4j.Logger;

public final class QueueManager implements QueueService {

    static final long FLIGHT_TIMEOUT_MILLIS = 30_000L;
    static final long EXPECT_MILLIS = 15_000L;
    static final long IDLE_REFRESH_MILLIS = 10_000L;

    private record Expected(String server, long until) {
    }

    private record Unit(List<UUID> party, boolean refused) {

        static final Unit SOLO = new Unit(List.of(), false);
        static final Unit REFUSED = new Unit(List.of(), true);
    }

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final ServerDirectory directory;
    private final NetworkStatusService status;
    private final ProxyMessages messages;
    private final EventBus events;
    private final Path file;
    private final Clock clock;
    private final QueueBook book = new QueueBook();
    private final QueueDisplay display;
    private final Map<String, Integer> probed = new ConcurrentHashMap<>();
    private final Set<String> closed = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Expected> expected = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile QueueSettings settings;
    private volatile Predicate<String> blocked = server -> false;
    private volatile Predicate<String> follows = group -> false;
    private volatile boolean refreshed;
    private volatile long lastRefresh;
    private volatile long lastDisplay;
    private volatile ScheduledTask ticker;

    public QueueManager(Object plugin, ProxyServer proxy, Logger logger, ServerDirectory directory,
                        NetworkStatusService status, ProxyMessages messages, EventBus events, QueueSettings settings,
                        Path file, Clock clock) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.directory = directory;
        this.status = status;
        this.messages = messages;
        this.events = events;
        this.settings = settings;
        this.file = file;
        this.clock = clock;
        this.display = new QueueDisplay(proxy, messages, this::targetName);
    }

    public void start(Predicate<String> maintenance, Predicate<String> partyFollows) {
        this.blocked = maintenance == null ? server -> false : maintenance;
        this.follows = partyFollows == null ? group -> false : partyFollows;
        proxy.getEventManager().register(plugin, this);
        schedule(settings.tickMillis());
        refresh(clock.millis());
    }

    public void stop() {
        ScheduledTask task = ticker;
        if (task != null) {
            task.cancel();
            ticker = null;
        }
        display.clear();
    }

    public void reload() {
        QueueSettings loaded = QueueSettings.load(file);
        loaded.warnings().forEach(warning -> logger.warn("{}: {}", QueueSettings.FILE_NAME, warning));
        int before = settings.tickMillis();
        settings = loaded;
        if (loaded.tickMillis() != before) {
            ScheduledTask task = ticker;
            if (task != null) {
                task.cancel();
            }
            schedule(loaded.tickMillis());
        }
        later();
    }

    public QueueSettings settings() {
        return settings;
    }

    @Override
    public CompletableFuture<QueueResult> join(UUID player, QueueTarget target) {
        try {
            return CompletableFuture.completedFuture(request(player, target));
        } catch (RuntimeException e) {
            logger.warn("Queue join of {} for {} failed", player, target.id(), e);
            return CompletableFuture.completedFuture(QueueResult.of(QueueStatus.FAILED));
        }
    }

    @Override
    public CompletableFuture<Boolean> leave(UUID player) {
        List<QueueBook.Removal> removed = book.leave(player, null, QueueLeftEvent.Reason.LEFT);
        announce(removed);
        return CompletableFuture.completedFuture(!removed.isEmpty());
    }

    @Override
    public CompletableFuture<Boolean> leave(UUID player, QueueTarget target) {
        List<QueueBook.Removal> removed = book.leave(player, target, QueueLeftEvent.Reason.LEFT);
        announce(removed);
        return CompletableFuture.completedFuture(!removed.isEmpty());
    }

    @Override
    public CompletableFuture<List<QueuePosition>> positions(UUID player) {
        return CompletableFuture.completedFuture(book.positions(player));
    }

    @Override
    public CompletableFuture<List<QueueInfo>> queues() {
        List<QueueInfo> infos = new ArrayList<>();
        for (QueueTarget target : book.targets()) {
            infos.add(info(target, false));
        }
        return CompletableFuture.completedFuture(List.copyOf(infos));
    }

    @Override
    public CompletableFuture<Optional<QueueInfo>> queue(QueueTarget target) {
        if (!exists(target) && !book.targets().contains(target)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.completedFuture(Optional.of(info(target, true)));
    }

    @Override
    public CompletableFuture<Boolean> pause(QueueTarget target, String actor) {
        boolean changed = book.pause(target);
        if (changed) {
            fire(new QueuePausedEvent(target, true, actor));
            tellQueued(target, "queue.paused");
        }
        return CompletableFuture.completedFuture(changed);
    }

    @Override
    public CompletableFuture<Boolean> resume(QueueTarget target, String actor) {
        boolean changed = book.resume(target);
        if (changed) {
            fire(new QueuePausedEvent(target, false, actor));
            tellQueued(target, "queue.resumed");
            later();
        }
        return CompletableFuture.completedFuture(changed);
    }

    @Override
    public CompletableFuture<Integer> clear(QueueTarget target) {
        List<QueueBook.Removal> removed = book.clear(target, QueueLeftEvent.Reason.CLEARED);
        announce(removed);
        return CompletableFuture.completedFuture(removed.size());
    }

    @Override
    public CompletableFuture<Optional<UUID>> sendNext(QueueTarget target) {
        Optional<QueueBook.Assignment> assignment = book.force(target, forcedSlots(target), clock.millis());
        assignment.ifPresent(found -> send(found, false));
        return CompletableFuture.completedFuture(assignment.map(QueueBook.Assignment::owner));
    }

    @Override
    public CompletableFuture<Boolean> joinable(String server, boolean joinable) {
        Optional<RegisteredServer> registered = proxy.getServer(server);
        if (registered.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("there is no server called " + server));
        }
        String key = lower(registered.get().getServerInfo().getName());
        boolean changed = joinable ? closed.remove(key) : closed.add(key);
        if (changed && joinable) {
            later();
        }
        return CompletableFuture.completedFuture(changed);
    }

    @Override
    public CompletableFuture<Set<String>> closedServers() {
        return CompletableFuture.completedFuture(Set.copyOf(closed));
    }

    @Subscribe(order = PostOrder.LATE)
    public void onPreConnect(ServerPreConnectEvent event) {
        if (!event.getResult().isAllowed()) {
            return;
        }
        Optional<RegisteredServer> target = event.getResult().getServer();
        Player player = event.getPlayer();
        if (target.isEmpty() || player.getCurrentServer().isEmpty()) {
            return;
        }
        try {
            if (intercept(player, target.get().getServerInfo().getName())) {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
            }
        } catch (RuntimeException e) {
            logger.warn("Queue check for {} failed", player.getUsername(), e);
        }
    }

    @Subscribe
    public void onPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        String server = current(player);
        expected.computeIfPresent(id, (key, value) -> value.server().equalsIgnoreCase(server) ? null : value);
        if (book.flightOf(id).isPresent()) {
            return;
        }
        if (event.getPreviousServer() == null) {
            List<QueuePosition> back = book.online(id);
            if (!back.isEmpty()) {
                messages.send(player, "queue.reconnected", display.resolvers(back.getFirst()));
                later();
            }
            return;
        }
        if (settings.leaveOnServerSwitch() && book.queued(id)) {
            announce(book.leave(id, null, QueueLeftEvent.Reason.SWITCHED_SERVER));
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        names.put(id, event.getPlayer().getUsername());
        expected.remove(id);
        display.forget(id);
        if (!book.queued(id)) {
            return;
        }
        if (settings.graceSeconds() == 0) {
            announce(book.leave(id, null, QueueLeftEvent.Reason.DISCONNECTED));
        } else {
            announce(book.offline(id, clock.millis()));
        }
    }

    Optional<QueueTarget> resolve(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        Optional<QueueTarget> explicit = QueueTarget.parse(input);
        if (explicit.isPresent()) {
            return explicit.filter(this::exists);
        }
        String name = lower(input.strip());
        if (!directory.byGroup(name).isEmpty()) {
            return Optional.of(QueueTarget.group(name));
        }
        return proxy.getServer(name).map(server -> QueueTarget.server(server.getServerInfo().getName()));
    }

    List<String> targetNames() {
        Set<String> options = new LinkedHashSet<>();
        for (ServerGroup group : directory.groups()) {
            if (!directory.byGroup(group.name()).isEmpty()) {
                options.add(group.name());
            }
        }
        for (NetworkServer server : directory.servers()) {
            options.add(server.group());
        }
        for (RegisteredServer server : proxy.getAllServers()) {
            options.add(lower(server.getServerInfo().getName()));
        }
        return List.copyOf(options);
    }

    List<String> queuedNames() {
        List<String> options = new ArrayList<>();
        for (QueueTarget target : book.targets()) {
            options.add(target.id());
        }
        return options;
    }

    Component targetName(QueueTarget target) {
        return messages.locale().format(rawName(target));
    }

    TagResolver[] resolvers(QueuePosition position) {
        return display.resolvers(position);
    }

    String nameOf(UUID player) {
        return proxy.getPlayer(player).map(Player::getUsername)
                .orElseGet(() -> names.getOrDefault(player, player.toString().substring(0, 8)));
    }

    boolean away(UUID player) {
        return book.away(player);
    }

    private QueueResult request(UUID id, QueueTarget target) {
        Optional<Player> online = proxy.getPlayer(id);
        if (online.isEmpty()) {
            return QueueResult.of(QueueStatus.OFFLINE);
        }
        Player player = online.get();
        if (!exists(target)) {
            return QueueResult.of(QueueStatus.UNKNOWN_TARGET);
        }
        String current = current(player);
        if (target.isServer() && target.name().equalsIgnoreCase(current)) {
            return QueueResult.of(QueueStatus.ALREADY_THERE);
        }
        String group = groupOf(target);
        QueueRules rules = settings.rules(target, group);
        boolean bypass = QueueSettings.bypasses(player::hasPermission, target, group);
        if (!rules.enabled() || bypass) {
            return direct(player, target, current, bypass);
        }
        Unit unit = unit(player, target, group);
        if (unit.refused()) {
            messages.send(player, "queue.not-leader");
            return QueueResult.of(QueueStatus.NOT_LEADER);
        }
        return enqueue(player, target, unit.party(), rules, true);
    }

    private boolean intercept(Player player, String server) {
        UUID id = player.getUniqueId();
        if (server.equalsIgnoreCase(current(player)) || blocked.test(server) || expecting(id, server)) {
            return false;
        }
        if (book.flightOf(id).filter(flight -> flight.server().equalsIgnoreCase(server)).isPresent()) {
            return false;
        }
        String group = directory.byName(server).map(NetworkServer::group).orElse(null);
        QueueTarget target = QueueTarget.server(server);
        QueueRules rules = settings.serverRules(server, group);
        if (!rules.enabled() || QueueSettings.bypasses(player::hasPermission, target, group)) {
            return false;
        }
        Unit unit = unit(player, target, group);
        boolean held = book.holds(target) || group != null && book.holds(QueueTarget.group(group));
        if (!held && room(server, group, unit.party().size() + 1)) {
            unit.party().forEach(member -> expect(member, server));
            return false;
        }
        if (!held && !online(server) && !rules.queueWhenOffline()) {
            return false;
        }
        if (unit.refused()) {
            return false;
        }
        return enqueue(player, target, unit.party(), rules, false).waiting();
    }

    private QueueResult enqueue(Player player, QueueTarget target, List<UUID> party, QueueRules rules,
                                boolean sendNow) {
        UUID id = player.getUniqueId();
        Optional<QueuePosition> existing = positionIn(id, target);
        if (existing.isPresent()) {
            messages.send(player, "queue.already", display.resolvers(existing.get()));
            return QueueResult.waiting(QueueStatus.ALREADY_QUEUED, existing.get());
        }
        if (!rules.queueWhenOffline() && !anyOnline(target)) {
            return QueueResult.of(QueueStatus.NO_SERVER);
        }
        QueueJoinEvent event = new QueueJoinEvent(target, id, party);
        events.publish(event);
        if (event.isCancelled()) {
            return QueueResult.of(QueueStatus.CANCELLED);
        }
        names.put(id, player.getUsername());
        party.forEach(member -> proxy.getPlayer(member).ifPresent(found -> names.put(member, found.getUsername())));
        QueueSettings current = settings;
        QueueBook.Joined joined = book.join(target, id, party, current.priority(player::hasPermission),
                current.onePerPlayer(), clock.millis());
        if (joined.status() == QueueStatus.ALREADY_QUEUED) {
            messages.send(player, "queue.already", display.resolvers(joined.position()));
            return QueueResult.waiting(QueueStatus.ALREADY_QUEUED, joined.position());
        }
        announce(joined.removed());
        if (sendNow) {
            for (QueueBook.Assignment assignment : dispatch(id)) {
                if (assignment.owner().equals(id) && assignment.target().equals(target)) {
                    return QueueResult.sent(assignment.server());
                }
            }
        } else {
            later();
        }
        QueuePosition position = positionIn(id, target).orElse(joined.position());
        tellJoined(player, position);
        fire(new QueueJoinedEvent(target, id, position.party(), position.position(), position.size()));
        return QueueResult.waiting(QueueStatus.QUEUED, position);
    }

    private QueueResult direct(Player player, QueueTarget target, String current, boolean bypass) {
        Optional<String> server = target.isServer() ? Optional.of(target.name())
                : pickDirect(target.name(), current, bypass);
        Optional<RegisteredServer> registered = server.flatMap(proxy::getServer);
        if (registered.isEmpty()) {
            return QueueResult.of(QueueStatus.NO_SERVER);
        }
        String name = registered.get().getServerInfo().getName();
        expect(player.getUniqueId(), name);
        player.createConnectionRequest(registered.get()).connect().whenComplete((result, error) -> {
            if (error != null || result.getStatus() == ConnectionRequestBuilder.Status.SERVER_DISCONNECTED) {
                messages.send(player, "queue.connect-failed", server(name));
            }
        });
        return QueueResult.sent(name);
    }

    private Optional<String> pickDirect(String group, String current, boolean bypass) {
        QueueBook.Slot best = null;
        String fallback = null;
        for (NetworkServer server : directory.byGroup(group)) {
            if (server.name().equalsIgnoreCase(current)) {
                continue;
            }
            Optional<QueueBook.Slot> slot = slot(server.name(), server.group());
            if (slot.isPresent() && (slot.get().unlimited() || slot.get().free() > book.inFlight(server.name()))) {
                if (best == null || better(slot.get(), best)) {
                    best = slot.get();
                }
            } else if (bypass && fallback == null && online(server.name()) && !blocked.test(server.name())) {
                fallback = server.name();
            }
        }
        return best != null ? Optional.of(best.server()) : Optional.ofNullable(fallback);
    }

    private static boolean better(QueueBook.Slot candidate, QueueBook.Slot best) {
        if (candidate.rank() != best.rank()) {
            return candidate.rank() < best.rank();
        }
        if (candidate.players() != best.players()) {
            return candidate.players() > best.players();
        }
        return candidate.server().compareToIgnoreCase(best.server()) < 0;
    }

    private Unit unit(Player player, QueueTarget target, String group) {
        UUID id = player.getUniqueId();
        Optional<Party> party = SocialApi.parties().flatMap(service -> service.partyOf(id));
        if (party.isEmpty() || party.get().size() < 2) {
            return Unit.SOLO;
        }
        QueueSettings.Party rules = settings.party();
        if (!party.get().isLeader(id)) {
            return rules.membersAlone() ? Unit.SOLO : Unit.REFUSED;
        }
        String followGroup = target.isGroup() ? target.name() : group == null ? NetworkServer.GROUP_DEFAULT : group;
        if (!rules.together() || !party.get().settings().follow() || !follows.test(followGroup)) {
            return Unit.SOLO;
        }
        List<UUID> members = new ArrayList<>();
        for (UUID member : party.get().memberIds()) {
            if (member.equals(id)) {
                continue;
            }
            Optional<Player> online = proxy.getPlayer(member);
            if (online.isEmpty() || target.isServer() && target.name().equalsIgnoreCase(current(online.get()))) {
                continue;
            }
            members.add(member);
        }
        return new Unit(List.copyOf(members), false);
    }

    private List<QueueBook.Assignment> dispatch(UUID quiet) {
        List<QueueBook.Assignment> assignments = book.plan(this::slots,
                target -> settings.rules(target, groupOf(target)).holdForParties(), clock.millis());
        for (QueueBook.Assignment assignment : assignments) {
            send(assignment, assignment.owner().equals(quiet));
        }
        return assignments;
    }

    private void send(QueueBook.Assignment assignment, boolean quiet) {
        Optional<RegisteredServer> server = proxy.getServer(assignment.server());
        Optional<Player> owner = proxy.getPlayer(assignment.owner());
        if (server.isEmpty() || owner.isEmpty()) {
            failed(assignment, null);
            return;
        }
        String name = server.get().getServerInfo().getName();
        QueueSendEvent event = new QueueSendEvent(assignment.target(), name, assignment.owner(), assignment.party());
        events.publish(event);
        if (event.isCancelled()) {
            book.retry(assignment.owner(), clock.millis(), settings.retryMillis());
            return;
        }
        List<CompletableFuture<?>> moves = new ArrayList<>();
        for (UUID id : assignment.players()) {
            proxy.getPlayer(id).ifPresent(player -> {
                if (!quiet || !id.equals(assignment.owner())) {
                    messages.send(player, "queue.sending", server(name));
                }
                display.hide(id, true);
                if (!id.equals(assignment.owner())) {
                    moves.add(player.createConnectionRequest(server.get()).connect().exceptionally(error -> null));
                }
            });
        }
        CompletableFuture.allOf(moves.toArray(CompletableFuture[]::new))
                .thenCompose(done -> owner.get().createConnectionRequest(server.get()).connect())
                .whenComplete((result, error) -> {
                    try {
                        if (error == null && (result.isSuccessful()
                                || result.getStatus() == ConnectionRequestBuilder.Status.ALREADY_CONNECTED)) {
                            landed(assignment, name);
                        } else {
                            failed(assignment, name);
                        }
                    } catch (RuntimeException e) {
                        logger.warn("Queue send of {} failed", assignment.owner(), e);
                    }
                });
    }

    private void landed(QueueBook.Assignment assignment, String server) {
        announce(book.landed(assignment.owner()));
        fire(new QueueSentEvent(assignment.target(), server, assignment.owner(), assignment.party(),
                Math.max(0L, clock.millis() - assignment.joinedAt()), book.size(assignment.target())));
        for (UUID player : assignment.players()) {
            if (!book.queued(player)) {
                display.hide(player, false);
            }
        }
    }

    private void failed(QueueBook.Assignment assignment, String server) {
        long now = clock.millis();
        boolean away = proxy.getPlayer(assignment.owner()).isEmpty();
        Optional<QueueBook.Removal> removal = book.failed(assignment.owner(), now, settings.retryMillis(),
                settings.maxSendAttempts());
        if (removal.isPresent()) {
            announce(List.of(removal.get()));
            return;
        }
        if (away) {
            announce(book.offline(assignment.owner(), now));
            return;
        }
        if (server != null) {
            proxy.getPlayer(assignment.owner()).ifPresent(player -> messages.send(player, "queue.send-failed",
                    server(server)));
        }
    }

    private void settle(QueueBook.Flight flight) {
        QueueBook.Assignment assignment = new QueueBook.Assignment(flight.target(), flight.owner(),
                flight.players().subList(1, flight.players().size()), flight.server(), 0, flight.since());
        boolean there = proxy.getPlayer(flight.owner()).map(this::current)
                .filter(server -> server.equalsIgnoreCase(flight.server())).isPresent();
        if (there) {
            landed(assignment, flight.server());
        } else {
            failed(assignment, flight.server());
        }
    }

    private void tick() {
        try {
            long now = clock.millis();
            QueueSettings current = settings;
            announce(book.expire(now, current.graceMillis()));
            expected.values().removeIf(entry -> entry.until() <= now);
            refresh(now);
            if (book.empty()) {
                display.clear();
                return;
            }
            prune();
            for (QueueBook.Flight flight : book.stale(now, FLIGHT_TIMEOUT_MILLIS)) {
                settle(flight);
            }
            dispatch(null);
            if (now - lastDisplay >= current.display().intervalSeconds() * 1000L) {
                lastDisplay = now;
                display.show(book.shown(), current.display());
            }
        } catch (RuntimeException e) {
            logger.warn("Queue tick failed", e);
        }
    }

    private void prune() {
        Optional<PartyService> parties = SocialApi.parties();
        announce(book.prune((owner, member) -> proxy.getPlayer(member).isPresent() && parties
                .flatMap(service -> service.partyOf(owner))
                .map(party -> party.contains(member))
                .orElse(false), QueueLeftEvent.Reason.PARTY_CHANGED));
    }

    private void refresh(long now) {
        long interval = book.empty() ? Math.max(IDLE_REFRESH_MILLIS, settings.statusRefreshSeconds() * 1000L)
                : settings.statusRefreshSeconds() * 1000L;
        if (now - lastRefresh < interval || !refreshing.compareAndSet(false, true)) {
            return;
        }
        lastRefresh = now;
        Map<String, Integer> connected = new HashMap<>();
        for (RegisteredServer server : proxy.getAllServers()) {
            connected.put(lower(server.getServerInfo().getName()), server.getPlayersConnected().size());
        }
        status.refresh().whenComplete((statuses, error) -> {
            if (error == null) {
                probed.putAll(connected);
                refreshed = true;
            }
            refreshing.set(false);
        });
    }

    private void schedule(int tickMillis) {
        ticker = proxy.getScheduler().buildTask(plugin, this::tick)
                .delay(tickMillis, TimeUnit.MILLISECONDS)
                .repeat(tickMillis, TimeUnit.MILLISECONDS)
                .schedule();
    }

    private void later() {
        if (!pending.compareAndSet(false, true)) {
            return;
        }
        proxy.getScheduler().buildTask(plugin, () -> {
            pending.set(false);
            try {
                dispatch(null);
            } catch (RuntimeException e) {
                logger.warn("Queue dispatch failed", e);
            }
        }).schedule();
    }

    private List<QueueBook.Slot> slots(QueueTarget target) {
        if (target.isServer()) {
            String group = directory.byName(target.name()).map(NetworkServer::group).orElse(null);
            if (group != null && book.paused(QueueTarget.group(group))) {
                return List.of();
            }
            return slot(target.name(), group).map(List::of).orElse(List.of());
        }
        List<QueueBook.Slot> slots = new ArrayList<>();
        for (NetworkServer server : directory.byGroup(target.name())) {
            if (!book.paused(QueueTarget.server(server.name()))) {
                slot(server.name(), server.group()).ifPresent(slots::add);
            }
        }
        return slots;
    }

    private Optional<QueueBook.Slot> slot(String server, String group) {
        Optional<RegisteredServer> registered = proxy.getServer(server);
        if (registered.isEmpty() || closed.contains(lower(server)) || blocked.test(server)) {
            return Optional.empty();
        }
        String name = registered.get().getServerInfo().getName();
        return Occupancy.slot(name, registered.get().getPlayersConnected().size(), status.status(name), refreshed,
                settings.serverRules(name, group), probed.getOrDefault(lower(name), -1));
    }

    private List<QueueBook.Slot> forcedSlots(QueueTarget target) {
        List<String> servers = target.isServer() ? List.of(target.name())
                : directory.byGroup(target.name()).stream().map(NetworkServer::name).toList();
        List<QueueBook.Slot> slots = new ArrayList<>();
        for (String server : servers) {
            Optional<RegisteredServer> registered = proxy.getServer(server);
            if (registered.isEmpty() || !online(server)) {
                continue;
            }
            GameState state = status.status(server).map(ServerStatus::state).orElse(GameState.UNKNOWN);
            slots.add(new QueueBook.Slot(registered.get().getServerInfo().getName(), QueueBook.Slot.UNLIMITED,
                    Occupancy.rank(state), registered.get().getPlayersConnected().size()));
        }
        return slots;
    }

    private boolean room(String server, String group, int size) {
        return slot(server, group).filter(found -> found.unlimited()
                || found.free() - book.inFlight(found.server()) >= size).isPresent();
    }

    private boolean online(String server) {
        if (!refreshed) {
            return true;
        }
        boolean connected = proxy.getServer(server).map(found -> !found.getPlayersConnected().isEmpty())
                .orElse(false);
        return connected || status.status(server).map(ServerStatus::online).orElse(false);
    }

    private boolean anyOnline(QueueTarget target) {
        if (target.isServer()) {
            return online(target.name());
        }
        for (NetworkServer server : directory.byGroup(target.name())) {
            if (online(server.name())) {
                return true;
            }
        }
        return false;
    }

    private boolean exists(QueueTarget target) {
        return target.isGroup() ? !directory.byGroup(target.name()).isEmpty()
                : proxy.getServer(target.name()).isPresent();
    }

    private String groupOf(QueueTarget target) {
        return target.isGroup() ? target.name()
                : directory.byName(target.name()).map(NetworkServer::group).orElse(null);
    }

    private Optional<QueuePosition> positionIn(UUID player, QueueTarget target) {
        return book.positions(player).stream().filter(position -> position.target().equals(target)).findFirst();
    }

    private void expect(UUID player, String server) {
        expected.put(player, new Expected(server, clock.millis() + EXPECT_MILLIS));
    }

    private boolean expecting(UUID player, String server) {
        Expected entry = expected.get(player);
        return entry != null && entry.server().equalsIgnoreCase(server) && entry.until() > clock.millis();
    }

    private QueueInfo info(QueueTarget target, boolean withEntries) {
        List<QueueEntry> entries = withEntries ? book.entries(target, this::nameOf) : List.of();
        return new QueueInfo(target, rawName(target), book.size(target), book.players(target), book.paused(target),
                entries);
    }

    private void announce(List<QueueBook.Removal> removals) {
        for (QueueBook.Removal removal : removals) {
            fire(new QueueLeftEvent(removal.target(), removal.player(), removal.party(), removal.reason(),
                    book.size(removal.target())));
            tell(removal);
            for (UUID player : removal.players()) {
                if (!book.queued(player)) {
                    display.hide(player, removal.reason() != QueueLeftEvent.Reason.SENT_ELSEWHERE);
                }
            }
        }
    }

    private void tell(QueueBook.Removal removal) {
        String own = switch (removal.reason()) {
            case SWITCHED_SERVER -> "queue.left-switch";
            case OTHER_QUEUE -> "queue.left-other";
            case PARTY_CHANGED -> "queue.left-unit";
            case CLEARED -> "queue.left-cleared";
            case FAILED -> "queue.left-failed";
            default -> null;
        };
        String others = switch (removal.reason()) {
            case LEFT, SWITCHED_SERVER, OTHER_QUEUE -> "queue.left-party";
            case DISCONNECTED -> "queue.left-party-offline";
            case CLEARED -> "queue.left-cleared";
            case FAILED -> "queue.left-failed";
            default -> null;
        };
        TagResolver target = Placeholder.component("target", targetName(removal.target()));
        TagResolver player = Placeholder.unparsed("player", nameOf(removal.player()));
        if (own != null) {
            proxy.getPlayer(removal.player()).ifPresent(online -> messages.send(online, own, target, player));
        }
        if (others != null) {
            for (UUID member : removal.party()) {
                proxy.getPlayer(member).ifPresent(online -> messages.send(online, others, target, player));
            }
        }
    }

    private void tellJoined(Player player, QueuePosition position) {
        TagResolver[] resolvers = display.resolvers(position);
        messages.send(player, position.party().isEmpty() ? "queue.joined" : "queue.joined-party", resolvers);
        if (position.paused()) {
            messages.send(player, "queue.joined-paused", resolvers);
        }
        TagResolver leader = Placeholder.unparsed("player", player.getUsername());
        for (UUID member : position.party()) {
            proxy.getPlayer(member).ifPresent(online -> {
                TagResolver[] memberResolvers = new TagResolver[resolvers.length + 1];
                System.arraycopy(resolvers, 0, memberResolvers, 0, resolvers.length);
                memberResolvers[resolvers.length] = leader;
                messages.send(online, "queue.party-queued", memberResolvers);
            });
        }
    }

    private void tellQueued(QueueTarget target, String key) {
        TagResolver name = Placeholder.component("target", targetName(target));
        for (QueueEntry entry : book.entries(target, this::nameOf)) {
            proxy.getPlayer(entry.owner()).ifPresent(player -> messages.send(player, key, name));
            for (UUID member : entry.party()) {
                proxy.getPlayer(member).ifPresent(player -> messages.send(player, key, name));
            }
        }
    }

    private void fire(PrismarinEvent event) {
        events.publish(event);
        ProxyRemote.relay(proxy, event);
    }

    private TagResolver server(String name) {
        return Placeholder.component("server", messages.locale().format(directory.byName(name)
                .map(NetworkServer::displayName).orElse(name)));
    }

    private String rawName(QueueTarget target) {
        if (target.isGroup()) {
            return directory.group(target.name()).map(ServerGroup::displayName).orElse(capitalize(target.name()));
        }
        return directory.byName(target.name()).map(NetworkServer::displayName).orElse(target.name());
    }

    private String current(Player player) {
        return player.getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse("");
    }

    private static String capitalize(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
