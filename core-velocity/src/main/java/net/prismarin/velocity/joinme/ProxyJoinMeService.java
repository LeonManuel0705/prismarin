package net.prismarin.velocity.joinme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeCreateEvent;
import net.prismarin.api.joinme.JoinMeCreatedEvent;
import net.prismarin.api.joinme.JoinMeExpiredEvent;
import net.prismarin.api.joinme.JoinMeHandler;
import net.prismarin.api.joinme.JoinMeJoinEvent;
import net.prismarin.api.joinme.JoinMePreviewEvent;
import net.prismarin.api.joinme.JoinMeRequest;
import net.prismarin.api.joinme.JoinMeResult;
import net.prismarin.api.joinme.JoinMeScope;
import net.prismarin.api.joinme.JoinMeService;
import net.prismarin.api.joinme.JoinMeSpot;
import net.prismarin.api.messaging.MessageBus;
import net.prismarin.api.messaging.NetworkMessage;
import net.prismarin.api.player.NetworkPlayer;
import net.prismarin.api.player.PlayerService;
import net.prismarin.api.queue.QueueApi;
import net.prismarin.api.server.GameState;
import net.prismarin.api.server.NetworkConnector;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerStatus;
import net.prismarin.api.server.ServerStatusService;
import net.prismarin.api.skin.SkinFaces;
import net.prismarin.api.social.SocialApi;
import net.prismarin.shared.joinme.JoinMeCodec;
import net.prismarin.shared.joinme.JoinMeDraft;
import net.prismarin.shared.joinme.JoinMeEndpoint;
import net.prismarin.shared.joinme.JoinMeLook;
import net.prismarin.shared.joinme.JoinMeRoute;
import net.prismarin.shared.joinme.JoinMeTerms;
import net.prismarin.velocity.admin.AdminContext;
import net.prismarin.velocity.admin.ModerationChannel;
import net.prismarin.velocity.admin.ProxyRemote;
import net.prismarin.velocity.admin.VanishRegistry;
import net.prismarin.velocity.locale.ProxyMessages;
import org.slf4j.Logger;

public final class ProxyJoinMeService implements JoinMeService, JoinMeEndpoint {

    public static final String CHANNEL = "prismarin:joinme";
    public static final String ARRIVAL = "joinme-arrival";
    public static final String TEXTURES = "textures";
    static final long FOLLOW_WINDOW_MILLIS = 60_000L;

    private final ProxyServer proxy;
    private final ProxyMessages messages;
    private final ServerStatusService status;
    private final NetworkConnector connector;
    private final VanishRegistry vanish;
    private final ModerationChannel channel;
    private final PlayerService players;
    private final EventBus events;
    private final MessageBus bus;
    private final Executor executor;
    private final Logger logger;
    private final ServerDirectory directory;
    private final SkinFaces faces;
    private volatile JoinMeRules rules;
    private volatile JoinMeBroadcaster broadcaster;
    private final JoinMeRegistry registry = new JoinMeRegistry();
    private final PendingArrivals arrivals = new PendingArrivals();
    private final Set<String> owned = ConcurrentHashMap.newKeySet();
    private final String origin = UUID.randomUUID().toString();

    public ProxyJoinMeService(AdminContext context, PlayerService players, JoinMeSettings settings,
                              SkinFaces faces, Executor executor, Logger logger) {
        this(context, context.services().events(), context.services().messageBus(), players, settings, faces,
                executor, logger);
    }

    ProxyJoinMeService(AdminContext context, EventBus events, MessageBus bus, PlayerService players,
                       JoinMeSettings settings, SkinFaces faces, Executor executor, Logger logger) {
        this.proxy = context.proxy();
        this.messages = context.messages();
        this.status = context.status();
        this.connector = context.connector();
        this.vanish = context.vanish();
        this.channel = context.channel();
        this.players = players;
        this.events = events;
        this.bus = bus;
        this.executor = executor;
        this.logger = logger;
        this.directory = context.directory();
        this.faces = faces;
        reload(settings);
    }

    public void reload(JoinMeSettings settings) {
        this.rules = new JoinMeRules(settings);
        this.broadcaster = new JoinMeBroadcaster(proxy, messages, directory, status, players, settings, faces,
                logger);
    }

    public void start() {
        bus.subscribe(CHANNEL, this::remote);
    }

    public JoinMeSettings settings() {
        return rules.settings();
    }

    @Override
    public CompletableFuture<JoinMeResult> create(JoinMeRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Player> creator = proxy.getPlayer(request.creator());
            if (creator.isEmpty()) {
                return JoinMeResult.of(JoinMeResult.Status.OFFLINE);
            }
            JoinMeSpot spot = spotOf(creator.get(), null);
            return createNow(JoinMeDraft.of(spot, request).withHandler("", true)
                    .withLook(new JoinMeLook(texturesOf(creator.get()), Map.of())));
        }, executor);
    }

    @Override
    public CompletableFuture<JoinMeResult> preview(JoinMeRequest request) {
        return CompletableFuture.supplyAsync(() -> proxy.getPlayer(request.creator()), executor).thenCompose(creator -> {
            if (creator.isEmpty()) {
                return CompletableFuture.completedFuture(JoinMeResult.of(JoinMeResult.Status.OFFLINE));
            }
            JoinMeSpot spot = spotOf(creator.get(), null);
            return preview(JoinMeDraft.of(spot, request.ignoringCooldown().withoutCost()).withHandler("", true)
                    .withLook(new JoinMeLook(texturesOf(creator.get()), Map.of())));
        });
    }

    @Override
    public CompletableFuture<JoinMeResult> preview(JoinMeDraft draft) {
        return CompletableFuture.supplyAsync(() -> previewNow(draft), executor).thenCompose(prepared -> {
            if (prepared.isEmpty()) {
                return CompletableFuture.completedFuture(JoinMeResult.of(JoinMeResult.Status.OFFLINE));
            }
            Preview preview = prepared.get();
            return broadcaster.preview(preview.viewer(), preview.joinMe(), preview.look())
                    .thenApply(sent -> JoinMeResult.of(sent ? JoinMeResult.Status.SUCCESS
                            : JoinMeResult.Status.OFFLINE));
        }).exceptionally(error -> {
            logger.warn("A JoinMe preview for {} failed", draft.spot().creator(), error);
            return JoinMeResult.of(JoinMeResult.Status.FAILED);
        });
    }

    @Override
    public CompletableFuture<List<JoinMe>> active() {
        long now = System.currentTimeMillis();
        return CompletableFuture.completedFuture(registry.all().stream().filter(joinMe -> joinMe.open(now)).toList());
    }

    @Override
    public CompletableFuture<List<JoinMe>> visibleTo(UUID viewer) {
        return list(viewer);
    }

    @Override
    public CompletableFuture<Optional<JoinMe>> find(String id) {
        return CompletableFuture.completedFuture(registry.get(id).map(JoinMeRegistry.Entry::joinMe));
    }

    @Override
    public CompletableFuture<JoinMeResult> join(UUID player, String id) {
        return CompletableFuture.supplyAsync(() -> registry.get(id), executor).thenCompose(entry -> {
            if (entry.isEmpty()) {
                return CompletableFuture.completedFuture(JoinMeResult.of(JoinMeResult.Status.NOT_FOUND));
            }
            return broadcaster.circles(entry.get().joinMe()).thenApplyAsync(circles -> joinNow(player, id, circles),
                    executor);
        }).exceptionally(error -> {
            logger.warn("A JoinMe click of {} failed", player, error);
            return JoinMeResult.of(JoinMeResult.Status.FAILED);
        });
    }

    @Override
    public CompletableFuture<Boolean> cancel(String id) {
        return cancel(id, null);
    }

    @Override
    public Subscription registerHandler(JoinMeScope scope, JoinMeHandler handler) {
        throw new UnsupportedOperationException("JoinMe handlers run on the game servers, register them there");
    }

    @Override
    public List<JoinMeHandler> handlers() {
        return List.of();
    }

    @Override
    public CompletableFuture<JoinMeTerms> check(JoinMeDraft draft) {
        return CompletableFuture.supplyAsync(() -> {
            JoinMeTerms terms = terms(draft);
            if (terms.allowed() && draft.look().hasTexture() && rules.settings().face().enabled()) {
                faces.fromTextures(draft.look().texture(), draft.spot().creator());
            }
            return terms;
        }, executor);
    }

    public String texturesOf(Player player) {
        return textures(players, player);
    }

    public static String textures(PlayerService players, Player player) {
        if (players.online(player.getUniqueId()).map(NetworkPlayer::nicked).orElse(false)) {
            return "";
        }
        return player.getGameProfile().getProperties().stream()
                .filter(property -> TEXTURES.equals(property.getName()))
                .map(property -> property.getValue())
                .findFirst()
                .orElse("");
    }

    @Override
    public CompletableFuture<JoinMeResult> create(JoinMeDraft draft) {
        return CompletableFuture.supplyAsync(() -> createNow(draft), executor);
    }

    @Override
    public CompletableFuture<List<JoinMe>> list(UUID viewer) {
        List<JoinMe> open = active().join();
        if (viewer == null) {
            return CompletableFuture.completedFuture(open);
        }
        List<CompletableFuture<Optional<JoinMe>>> checks = new ArrayList<>();
        for (JoinMe joinMe : open) {
            checks.add(broadcaster.circles(joinMe).thenApply(circles -> joinMe.creator().equals(viewer)
                    || circles.contains(joinMe.audience(), viewer) ? Optional.of(joinMe) : Optional.<JoinMe>empty()));
        }
        return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new)).thenApply(done -> checks.stream()
                .map(CompletableFuture::join).flatMap(Optional::stream).toList());
    }

    @Override
    public CompletableFuture<Boolean> cancel(String id, UUID creator) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<String> target = id != null ? registry.get(id).map(entry -> entry.joinMe().id())
                    : creator == null ? Optional.empty() : registry.byCreator(creator).map(entry -> entry.joinMe().id());
            target.ifPresent(found -> end(found, JoinMeExpiredEvent.Reason.CANCELLED, true));
            return target.isPresent();
        }, executor);
    }

    public void sweep() {
        long now = System.currentTimeMillis();
        for (JoinMe joinMe : registry.expire(now)) {
            ended(joinMe, JoinMeExpiredEvent.Reason.EXPIRED, owned.remove(joinMe.id()));
        }
        for (PendingArrivals.Pending gone : arrivals.expired(now)) {
            if (gone.reserved()) {
                registry.release(gone.joinMe().id());
            }
        }
        registry.forgetStamps(now - JoinMeSettings.MAX_SECONDS * 1000L);
    }

    @Subscribe
    public void onServerConnected(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        String server = broadcaster.serverOf(player);
        arrivals.take(player.getUniqueId(), server, System.currentTimeMillis())
                .ifPresent(pending -> executor.execute(() -> deliver(player, pending)));
        if (event.getPreviousServer() != null && rules.settings().create().endWhenCreatorLeaves()) {
            registry.byCreator(player.getUniqueId())
                    .filter(entry -> !entry.joinMe().server().equalsIgnoreCase(server))
                    .ifPresent(entry -> executor.execute(() -> end(entry.joinMe().id(),
                            JoinMeExpiredEvent.Reason.CREATOR_LEFT, true)));
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        arrivals.forget(uuid).filter(PendingArrivals.Pending::reserved)
                .ifPresent(pending -> registry.release(pending.joinMe().id()));
        if (rules.settings().create().endWhenCreatorLeaves()) {
            registry.byCreator(uuid).ifPresent(entry -> executor.execute(() -> end(entry.joinMe().id(),
                    JoinMeExpiredEvent.Reason.CREATOR_LEFT, true)));
        }
    }

    private JoinMeTerms terms(JoinMeDraft draft) {
        UUID uuid = draft.spot().creator();
        Optional<Player> creator = proxy.getPlayer(uuid);
        JoinMeSpot spot = creator.map(player -> spotOf(player, draft.spot())).orElse(draft.spot());
        Predicate<String> permissions = creator.<Predicate<String>>map(player -> player::hasPermission)
                .orElse(permission -> false);
        long now = System.currentTimeMillis();
        JoinMeTerms terms = rules.check(new JoinMeRules.Creator(creator.isPresent(), vanish.vanished(uuid),
                spot.group(), spot.state(), registry.cooldownLeft(uuid, rules.cooldownMillis(permissions), now),
                permissions), draft);
        if (terms.allowed() && !JoinMeRoute.joinable(draft.handled(), terms.sameServer(), draft.worldFallback())) {
            return JoinMeTerms.refused(JoinMeResult.Status.NOTHING_TO_JOIN);
        }
        return terms;
    }

    private record Preview(Player viewer, JoinMe joinMe, JoinMeLook look) {
    }

    private Optional<Preview> previewNow(JoinMeDraft draft) {
        UUID uuid = draft.spot().creator();
        Optional<Player> creator = proxy.getPlayer(uuid);
        if (creator.isEmpty()) {
            return Optional.empty();
        }
        Player player = creator.get();
        JoinMeSpot spot = spotOf(player, draft.spot());
        Predicate<String> permissions = player::hasPermission;
        long lifetime = draft.lifetimeMillis() > 0L ? Math.min(draft.lifetimeMillis(),
                JoinMeSettings.MAX_SECONDS * 1000L) : rules.lifetimeMillis(permissions);
        int maxJoins = draft.maxJoins() >= 0 ? draft.maxJoins() : rules.maxJoins(permissions);
        JoinMePreviewEvent event = new JoinMePreviewEvent(spot, draft.placeholders(), draft.look().lines());
        events.publish(event);
        long now = System.currentTimeMillis();
        String shown = draft.shownName().isBlank()
                ? players.online(uuid).map(NetworkPlayer::shownName).orElse(player.getUsername())
                : draft.shownName();
        JoinMe joinMe = new JoinMe(JoinMeBroadcaster.PREVIEW_ID, uuid, player.getUsername(), shown, spot.server(),
                spot.group(), spot.world(), spot.state(), draft.handler(),
                JoinMeCodec.cleanPlaceholders(event.placeholders()),
                draft.audience() != null ? draft.audience() : rules.settings().audience().audience(), maxJoins, 0,
                now, now + lifetime);
        return Optional.of(new Preview(player, joinMe, draft.look().withLines(new TreeMap<>(event.lines()))));
    }

    private JoinMeResult createNow(JoinMeDraft draft) {
        JoinMeTerms terms = terms(draft);
        if (!terms.allowed()) {
            return terms.refusal();
        }
        UUID uuid = draft.spot().creator();
        Optional<Player> creator = proxy.getPlayer(uuid);
        if (creator.isEmpty()) {
            return JoinMeResult.of(JoinMeResult.Status.OFFLINE);
        }
        JoinMeSpot spot = spotOf(creator.get(), draft.spot());
        JoinMeCreateEvent event = new JoinMeCreateEvent(spot, draft.placeholders(), draft.look().lines(),
                Duration.ofMillis(terms.lifetimeMillis()), terms.maxJoins(), terms.audience());
        events.publish(event);
        if (event.isCancelled()) {
            return JoinMeResult.of(JoinMeResult.Status.CANCELLED);
        }
        registry.byCreator(uuid).ifPresent(old -> end(old.joinMe().id(), JoinMeExpiredEvent.Reason.REPLACED, true));
        long now = System.currentTimeMillis();
        String shown = draft.shownName().isBlank()
                ? players.online(uuid).map(NetworkPlayer::shownName).orElse(creator.get().getUsername())
                : draft.shownName();
        JoinMe joinMe = new JoinMe(registry.newId(), uuid, creator.get().getUsername(), shown, spot.server(),
                spot.group(), spot.world(), spot.state(), draft.handler(),
                JoinMeCodec.cleanPlaceholders(event.placeholders()), event.audience(), event.maxJoins(), 0, now,
                now + event.lifetime().toMillis());
        JoinMeLook look = draft.look().withLines(new TreeMap<>(event.lines()));
        registry.put(joinMe, draft.worldFallback(), look);
        registry.stamp(uuid, now);
        owned.add(joinMe.id());
        publish(joinMe, draft.worldFallback(), look);
        broadcaster.broadcast(joinMe, look).whenComplete((count, error) -> {
            if (error != null) {
                logger.warn("Could not send the JoinMe of {}", joinMe.creatorName(), error);
            } else {
                logger.info("JoinMe {} of {} on {} went to {} players", joinMe.id(), joinMe.creatorName(),
                        joinMe.server(), count);
            }
        });
        JoinMeCreatedEvent created = new JoinMeCreatedEvent(joinMe);
        events.publish(created);
        ProxyRemote.relay(proxy, created);
        return JoinMeResult.success(joinMe);
    }

    private JoinMeResult joinNow(UUID uuid, String id, AudienceSelector.Circles circles) {
        Optional<JoinMeRegistry.Entry> found = registry.get(id);
        if (found.isEmpty()) {
            return JoinMeResult.of(JoinMeResult.Status.NOT_FOUND);
        }
        Optional<Player> player = proxy.getPlayer(uuid);
        JoinMe joinMe = refreshWorld(found.get().joinMe());
        String server = player.map(broadcaster::serverOf).orElse("");
        String world = players.online(uuid).map(NetworkPlayer::world).orElse("");
        String permission = rules.settings().join().permission();
        JoinMeRules.Clicker clicker = new JoinMeRules.Clicker(uuid, player.isPresent(), server, world,
                permission.isEmpty() || player.map(online -> online.hasPermission(permission)).orElse(false),
                circles.contains(joinMe.audience(), uuid));
        long now = System.currentTimeMillis();
        JoinMeRegistry.Entry entry = registry.get(id).orElse(found.get());
        JoinMeRules.Verdict verdict = rules.join(joinMe, entry.worldFallback(), entry.reserved(), clicker,
                target(joinMe.server()), now);
        if (!verdict.allowed()) {
            return verdict.status() == JoinMeResult.Status.NOT_FOUND ? JoinMeResult.of(verdict.status())
                    : JoinMeResult.of(verdict.status(), joinMe);
        }
        JoinMeJoinEvent event = new JoinMeJoinEvent(uuid, joinMe);
        events.publish(event);
        if (event.isCancelled()) {
            return JoinMeResult.of(JoinMeResult.Status.CANCELLED, joinMe);
        }
        JoinMeResult.Status reserved = registry.reserve(id, now);
        if (!reserved.success()) {
            return JoinMeResult.of(reserved, joinMe);
        }
        go(player.get(), joinMe, verdict.route(), now);
        return JoinMeResult.success(joinMe);
    }

    private void go(Player player, JoinMe joinMe, JoinMeRoute route, long now) {
        PendingArrivals.Pending pending = new PendingArrivals.Pending(player.getUniqueId(), joinMe, false, true,
                now + rules.settings().join().arrivalTimeoutMillis());
        arrivals.expect(pending).filter(PendingArrivals.Pending::reserved)
                .ifPresent(previous -> registry.release(previous.joinMe().id()));
        if (route == JoinMeRoute.ARRIVE) {
            arrivals.forget(player.getUniqueId());
            deliver(player, pending);
            return;
        }
        followers(player, joinMe, now);
        connector.connect(player.getUniqueId(), joinMe.server())
                .thenCompose(connected -> Boolean.TRUE.equals(connected)
                        ? CompletableFuture.completedFuture(true) : queued(player.getUniqueId()))
                .whenComplete((connected, error) -> {
                    if (error == null && Boolean.TRUE.equals(connected)) {
                        return;
                    }
                    arrivals.forget(player.getUniqueId()).filter(PendingArrivals.Pending::reserved)
                            .ifPresent(gone -> registry.release(gone.joinMe().id()));
                    if (player.isActive()) {
                        messages.send(player, "joinme.connect-failed",
                                Placeholder.unparsed("server", joinMe.server()));
                    }
                });
    }

    private static CompletableFuture<Boolean> queued(UUID player) {
        return QueueApi.service().map(queue -> queue.positions(player)
                        .thenApply(found -> !found.isEmpty())
                        .exceptionally(error -> false))
                .orElseGet(() -> CompletableFuture.completedFuture(false));
    }

    private void followers(Player leader, JoinMe joinMe, long now) {
        UUID uuid = leader.getUniqueId();
        SocialApi.parties().flatMap(parties -> parties.partyOf(uuid))
                .filter(party -> party.isLeader(uuid) && party.size() > 1 && party.settings().follow())
                .ifPresent(party -> {
                    for (UUID member : party.memberIds()) {
                        if (member.equals(uuid)) {
                            continue;
                        }
                        proxy.getPlayer(member)
                                .filter(online -> !joinMe.server().equalsIgnoreCase(broadcaster.serverOf(online)))
                                .ifPresent(online -> arrivals.expect(new PendingArrivals.Pending(member, joinMe, true,
                                        false, now + FOLLOW_WINDOW_MILLIS)).filter(PendingArrivals.Pending::reserved)
                                        .ifPresent(previous -> registry.release(previous.joinMe().id())));
                    }
                });
    }

    private void deliver(Player player, PendingArrivals.Pending pending) {
        if (!player.isActive()) {
            if (pending.reserved()) {
                registry.release(pending.joinMe().id());
            }
            return;
        }
        JoinMe current = registry.get(pending.joinMe().id()).map(JoinMeRegistry.Entry::joinMe)
                .orElse(pending.joinMe());
        JsonObject message = new JsonObject();
        message.addProperty("type", ARRIVAL);
        message.add("joinme", JoinMeCodec.encode(current));
        message.addProperty("party", pending.party());
        channel.sendSocial(player, message);
        if (pending.party()) {
            return;
        }
        Optional<JoinMe> counted = registry.arrived(current.id(), pending.reserved());
        if (counted.isEmpty()) {
            return;
        }
        publishJoined(current.id());
        JoinMe updated = counted.get();
        if (rules.settings().join().notifyCreator()) {
            proxy.getPlayer(updated.creator()).ifPresent(creator -> messages.send(creator, "joinme.joined",
                    joinedResolver(creator, player, updated)));
        }
        if (updated.usedUp()) {
            end(updated.id(), JoinMeExpiredEvent.Reason.USED_UP, true);
        }
    }

    private TagResolver joinedResolver(Player creator, Player joined, JoinMe joinMe) {
        String name = players.online(joined.getUniqueId())
                .map(found -> players.displayName(creator.getUniqueId(), found))
                .orElse(joined.getUsername());
        return TagResolver.resolver(broadcaster.resolver(creator, joinMe), Placeholder.unparsed("player", name));
    }

    private void end(String id, JoinMeExpiredEvent.Reason reason, boolean announce) {
        registry.remove(id).ifPresent(joinMe -> {
            boolean mine = owned.remove(id);
            if (announce) {
                publishEnded(id, reason);
            }
            ended(joinMe, reason, mine || announce);
        });
    }

    private void ended(JoinMe joinMe, JoinMeExpiredEvent.Reason reason, boolean relay) {
        JoinMeExpiredEvent event = new JoinMeExpiredEvent(joinMe, reason);
        events.publish(event);
        if (!relay) {
            return;
        }
        ProxyRemote.relay(proxy, event);
        if (reason == JoinMeExpiredEvent.Reason.REPLACED) {
            return;
        }
        proxy.getPlayer(joinMe.creator()).ifPresent(creator -> messages.send(creator,
                "joinme.ended." + reason.id(), broadcaster.resolver(creator, joinMe)));
    }

    private JoinMe refreshWorld(JoinMe joinMe) {
        return players.online(joinMe.creator())
                .filter(creator -> creator.onServer(joinMe.server()))
                .flatMap(creator -> registry.moved(joinMe.id(), creator.world()))
                .orElse(joinMe);
    }

    private JoinMeRules.Target target(String server) {
        Optional<ServerStatus> known = status.status(server);
        if (known.isEmpty()) {
            return proxy.getServer(server).isPresent() ? JoinMeRules.Target.UNKNOWN
                    : new JoinMeRules.Target(false, GameState.UNKNOWN);
        }
        return new JoinMeRules.Target(known.get().online(), known.get().state());
    }

    private JoinMeSpot spotOf(Player player, JoinMeSpot reported) {
        String server = broadcaster.serverOf(player);
        if (server.isEmpty() && reported != null) {
            server = reported.server();
        }
        String world = reported != null && !reported.world().isEmpty() ? reported.world()
                : players.online(player.getUniqueId()).map(NetworkPlayer::world).orElse("");
        GameState state = reported != null ? reported.state()
                : status.status(server).map(ServerStatus::state).orElse(GameState.UNKNOWN);
        return new JoinMeSpot(player.getUniqueId(), server, broadcaster.groupOf(server), world, state);
    }

    private void publish(JoinMe joinMe, boolean worldFallback, JoinMeLook look) {
        JsonObject payload = payload("created");
        payload.add("joinme", JoinMeCodec.encode(joinMe));
        payload.addProperty("world-fallback", worldFallback);
        payload.add("look", JoinMeCodec.encode(look));
        send(payload);
    }

    private void publishEnded(String id, JoinMeExpiredEvent.Reason reason) {
        JsonObject payload = payload("ended");
        payload.addProperty("id", id);
        payload.addProperty("reason", reason.id());
        send(payload);
    }

    private void publishJoined(String id) {
        JsonObject payload = payload("joined");
        payload.addProperty("id", id);
        payload.addProperty("delta", 1);
        send(payload);
    }

    private JsonObject payload(String op) {
        JsonObject payload = new JsonObject();
        payload.addProperty("origin", origin);
        payload.addProperty("op", op);
        return payload;
    }

    private void send(JsonObject payload) {
        try {
            bus.publish(CHANNEL, payload.toString());
        } catch (RuntimeException e) {
            logger.debug("Could not share a JoinMe change with the other proxies", e);
        }
    }

    private void remote(NetworkMessage message) {
        try {
            JsonObject payload = JsonParser.parseString(message.payloadJson()).getAsJsonObject();
            JsonElement sender = payload.get("origin");
            if (sender == null || origin.equals(sender.getAsString())) {
                return;
            }
            switch (payload.get("op").getAsString()) {
                case "created" -> JoinMeCodec.joinMe(payload.get("joinme")).ifPresent(joinMe -> {
                    JsonElement fallback = payload.get("world-fallback");
                    JoinMeLook look = JoinMeCodec.look(payload.get("look"));
                    registry.put(joinMe, fallback == null || fallback.getAsBoolean(), look);
                    registry.stamp(joinMe.creator(), joinMe.createdAt());
                    broadcaster.broadcast(joinMe, look);
                    events.publish(new JoinMeCreatedEvent(joinMe));
                });
                case "ended" -> registry.remove(payload.get("id").getAsString()).ifPresent(joinMe -> ended(joinMe,
                        JoinMeExpiredEvent.Reason.byId(payload.get("reason").getAsString())
                                .orElse(JoinMeExpiredEvent.Reason.EXPIRED), false));
                case "joined" -> registry.addJoins(payload.get("id").getAsString(), payload.get("delta").getAsInt())
                        .filter(joinMe -> joinMe.usedUp() && owned.contains(joinMe.id()))
                        .ifPresent(joinMe -> end(joinMe.id(), JoinMeExpiredEvent.Reason.USED_UP, true));
                default -> {
                }
            }
        } catch (RuntimeException e) {
            logger.debug("Ignoring a malformed JoinMe message from {}", message.sourceServer(), e);
        }
    }
}
