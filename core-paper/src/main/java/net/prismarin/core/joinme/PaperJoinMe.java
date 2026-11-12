package net.prismarin.core.joinme;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeArrival;
import net.prismarin.api.joinme.JoinMeArrivedEvent;
import net.prismarin.api.joinme.JoinMeCreateEvent;
import net.prismarin.api.joinme.JoinMeCreatedEvent;
import net.prismarin.api.joinme.JoinMeExpiredEvent;
import net.prismarin.api.joinme.JoinMeHandler;
import net.prismarin.api.joinme.JoinMeJoinEvent;
import net.prismarin.api.joinme.JoinMeOutcome;
import net.prismarin.api.joinme.JoinMePreviewEvent;
import net.prismarin.api.joinme.JoinMeRequest;
import net.prismarin.api.joinme.JoinMeResult;
import net.prismarin.api.joinme.JoinMeScope;
import net.prismarin.api.joinme.JoinMeService;
import net.prismarin.api.joinme.JoinMeSpot;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.player.NetworkPlayer;
import net.prismarin.api.player.PlayerService;
import net.prismarin.api.player.ViewerRules;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.server.NetworkConnector;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.skin.SkinFaceApi;
import net.prismarin.api.vanish.VanishApi;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.moderation.ModerationChannel;
import net.prismarin.core.network.ArrivalTracker;
import net.prismarin.core.remote.PaperProxyLink;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.joinme.ArrivalPlan;
import net.prismarin.shared.joinme.JoinMeCodec;
import net.prismarin.shared.joinme.JoinMeDraft;
import net.prismarin.shared.joinme.JoinMeHandlerRegistry;
import net.prismarin.shared.joinme.JoinMeLook;
import net.prismarin.shared.joinme.JoinMePlaceholders;
import net.prismarin.shared.joinme.JoinMeTerms;
import net.prismarin.shared.network.JoinMeCalls;
import net.prismarin.shared.network.ProxyLink;
import net.prismarin.shared.skin.SkinFaceService;
import net.prismarin.shared.status.SimpleGameStateService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

@Singleton
public final class PaperJoinMe implements JoinMeService {

    public static final String TEXTURES = "textures";

    private final PrismarinCorePlugin plugin;
    private final CoreConfig config;
    private final ServerDirectory directory;
    private final SimpleGameStateService gameState;
    private final EconomyService economy;
    private final EventBus events;
    private final PlayerService players;
    private final ProfileService profiles;
    private final LocaleService locale;
    private final NetworkConnector connector;
    private final ArrivalTracker arrivals;
    private final ModerationChannel channel;
    private volatile BackendJoinMeSettings settings;
    private final JoinMeCalls calls;
    private final ExecutorService executor;
    private final JoinMeHandlerRegistry handlers = new JoinMeHandlerRegistry();
    private final Map<String, JoinMe> known = new ConcurrentHashMap<>();
    private final List<Subscription> subscriptions = new ArrayList<>();

    @Inject
    public PaperJoinMe(PrismarinCorePlugin plugin, CoreConfig config, ServerDirectory directory,
                       SimpleGameStateService gameState, EconomyService economy, EventBus events,
                       PlayerService players, ProfileService profiles, LocaleService locale,
                       NetworkConnector connector, ArrivalTracker arrivals, ModerationChannel channel,
                       PaperProxyLink link, ExecutorService executor) {
        this.plugin = plugin;
        this.config = config;
        this.directory = directory;
        this.gameState = gameState;
        this.economy = economy;
        this.events = events;
        this.players = players;
        this.profiles = profiles;
        this.locale = locale;
        this.connector = connector;
        this.arrivals = arrivals;
        this.channel = channel;
        this.settings = BackendJoinMeSettings.load(plugin.getDataFolder().toPath()
                .resolve(BackendJoinMeSettings.FILE_NAME));
        this.calls = new JoinMeCalls(link);
        this.executor = executor;
    }

    public void start() {
        settings.problems().forEach(problem -> plugin.getLogger().warning(BackendJoinMeSettings.FILE_NAME + ": "
                + problem));
        channel.handleJoinMe(this::arrival);
        subscriptions.add(events.subscribe(JoinMeCreatedEvent.class, event -> known.put(event.joinMe().id(),
                event.joinMe())));
        subscriptions.add(events.subscribe(JoinMeExpiredEvent.class, event -> known.remove(event.joinMe().id())));
        SkinFaceService faces = SkinFaceService.create(uuid -> sync(() -> Optional
                        .ofNullable(plugin.getServer().getPlayer(uuid))
                        .map(PaperJoinMe::textures).filter(value -> !value.isEmpty())), executor,
                plugin.getDataFolder().toPath().resolve(SkinFaceService.FOLDER), settings.faceCache());
        SkinFaceApi.register(faces);
    }

    public static String textures(Player player) {
        for (ProfileProperty property : player.getPlayerProfile().getProperties()) {
            if (TEXTURES.equals(property.getName())) {
                return property.getValue();
            }
        }
        return "";
    }

    public void stop() {
        SkinFaceApi.unregister();
        channel.handleJoinMe(null);
        subscriptions.forEach(Subscription::unsubscribe);
        subscriptions.clear();
        known.clear();
    }

    public BackendJoinMeSettings settings() {
        return settings;
    }

    public void reload() {
        BackendJoinMeSettings fresh = BackendJoinMeSettings.load(plugin.getDataFolder().toPath()
                .resolve(BackendJoinMeSettings.FILE_NAME));
        fresh.problems().forEach(problem -> plugin.getLogger().warning(BackendJoinMeSettings.FILE_NAME + ": "
                + problem));
        settings = fresh;
    }

    public Collection<JoinMe> known() {
        long now = System.currentTimeMillis();
        known.values().removeIf(joinMe -> !joinMe.open(now));
        return List.copyOf(known.values());
    }

    @Override
    public CompletableFuture<JoinMeResult> create(JoinMeRequest request) {
        return sync(() -> draft(request, false)).thenCompose(prepared -> {
            if (prepared.refusal() != null) {
                return CompletableFuture.completedFuture(JoinMeResult.of(prepared.refusal()));
            }
            return calls.check(prepared.draft()).thenCompose(terms -> afterCheck(prepared.draft(), terms));
        }).exceptionally(error -> {
            plugin.getLogger().log(Level.WARNING, "Could not create a JoinMe for " + request.creator(), error);
            return JoinMeResult.of(JoinMeResult.Status.FAILED);
        });
    }

    @Override
    public CompletableFuture<JoinMeResult> preview(JoinMeRequest request) {
        JoinMeRequest free = request.ignoringCooldown().withoutCost();
        return sync(() -> draft(free, true)).thenCompose(prepared -> {
            if (prepared.refusal() != null) {
                return CompletableFuture.completedFuture(JoinMeResult.of(prepared.refusal()));
            }
            return calls.preview(prepared.draft());
        }).exceptionally(error -> {
            plugin.getLogger().log(Level.WARNING, "Could not preview a JoinMe for " + request.creator(), error);
            return JoinMeResult.of(JoinMeResult.Status.FAILED);
        });
    }

    @Override
    public CompletableFuture<List<JoinMe>> active() {
        return calls.list(null).thenApply(this::remember);
    }

    @Override
    public CompletableFuture<List<JoinMe>> visibleTo(UUID viewer) {
        return calls.list(viewer).thenApply(this::remember);
    }

    @Override
    public CompletableFuture<Optional<JoinMe>> find(String id) {
        return calls.find(id).thenApply(found -> {
            found.ifPresentOrElse(joinMe -> known.put(joinMe.id(), joinMe), () -> {
                if (id != null) {
                    known.remove(id);
                }
            });
            return found;
        });
    }

    @Override
    public CompletableFuture<JoinMeResult> join(UUID player, String id) {
        if (!JoinMeCodec.validId(id)) {
            return CompletableFuture.completedFuture(JoinMeResult.of(JoinMeResult.Status.NOT_FOUND));
        }
        JoinMe cached = known.get(id);
        CompletableFuture<Optional<JoinMe>> lookup = cached != null
                ? CompletableFuture.completedFuture(Optional.of(cached)) : find(id);
        return lookup.thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(JoinMeResult.of(JoinMeResult.Status.NOT_FOUND));
            }
            return sync(() -> {
                JoinMeJoinEvent event = new JoinMeJoinEvent(player, found.get());
                events.publish(event);
                return event.isCancelled();
            }).thenCompose(cancelled -> cancelled
                    ? CompletableFuture.completedFuture(JoinMeResult.of(JoinMeResult.Status.CANCELLED, found.get()))
                    : calls.join(player, id));
        }).exceptionally(error -> JoinMeResult.of(unreachable(error)));
    }

    @Override
    public CompletableFuture<Boolean> cancel(String id) {
        return calls.cancel(id, null).whenComplete((done, error) -> {
            if (id != null) {
                known.remove(id);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> cancelFor(UUID creator) {
        return calls.cancel(null, creator).whenComplete((done, error) ->
                known.values().removeIf(joinMe -> joinMe.creator().equals(creator)));
    }

    @Override
    public Subscription registerHandler(JoinMeScope scope, JoinMeHandler handler) {
        return handlers.register(scope, handler);
    }

    @Override
    public List<JoinMeHandler> handlers() {
        return handlers.handlers();
    }

    private record Prepared(JoinMeDraft draft, JoinMeResult.Status refusal) {
    }

    private Prepared draft(JoinMeRequest request, boolean preview) {
        Player player = plugin.getServer().getPlayer(request.creator());
        if (player == null) {
            return new Prepared(null, JoinMeResult.Status.OFFLINE);
        }
        if (!preview && VanishApi.service().map(vanish -> vanish.vanished(player.getUniqueId())).orElse(false)) {
            return new Prepared(null, JoinMeResult.Status.VANISHED);
        }
        JoinMeSpot spot = spot(player.getUniqueId(), player.getWorld().getName());
        Optional<JoinMeHandler> handler = handlers.select(spot);
        Map<Integer, String> lines = new TreeMap<>();
        Map<String, String> values = new LinkedHashMap<>();
        values.put(JoinMePlaceholders.PLAYERS, Integer.toString(plugin.getServer().getOnlinePlayers().size()));
        values.put(JoinMePlaceholders.MAX_PLAYERS, Integer.toString(plugin.getServer().getMaxPlayers()));
        values.put(JoinMePlaceholders.WORLD_PLAYERS, Integer.toString(player.getWorld().getPlayers().size()));
        if (handler.isPresent()) {
            try {
                if (!preview && !handler.get().allows(spot)) {
                    return new Prepared(null, JoinMeResult.Status.REFUSED);
                }
                values.putAll(handler.get().placeholders(spot));
                lines.putAll(handler.get().lines(spot));
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "JoinMe handler " + handler.get().id() + " failed", e);
            }
        }
        values.putAll(request.placeholders());
        String shown = players.online(player.getUniqueId()).map(NetworkPlayer::shownName).orElse(player.getName());
        JoinMeDraft draft = JoinMeDraft.of(spot, request).withShownName(shown)
                .withHandler(handler.map(JoinMeHandler::id).orElse(""), settings.sameWorld())
                .withPlaceholders(values).withLook(new JoinMeLook(textures(player), lines));
        if (preview) {
            JoinMePreviewEvent event = new JoinMePreviewEvent(spot, draft.placeholders(), draft.look().lines());
            events.publish(event);
            draft = draft.withPlaceholders(event.placeholders())
                    .withLook(draft.look().withLines(new TreeMap<>(event.lines())));
        }
        return new Prepared(draft, null);
    }

    private CompletableFuture<JoinMeResult> afterCheck(JoinMeDraft draft, JoinMeTerms terms) {
        if (!terms.allowed()) {
            return CompletableFuture.completedFuture(terms.refusal());
        }
        return sync(() -> {
            JoinMeCreateEvent event = new JoinMeCreateEvent(draft.spot(), draft.placeholders(), draft.look().lines(),
                    Duration.ofMillis(Math.max(1_000L, terms.lifetimeMillis())), terms.maxJoins(), terms.audience());
            events.publish(event);
            return event;
        }).thenCompose(event -> {
            if (event.isCancelled()) {
                return CompletableFuture.completedFuture(JoinMeResult.of(JoinMeResult.Status.CANCELLED));
            }
            JoinMeDraft finished = draft.withPlaceholders(event.placeholders())
                    .withTerms(event.lifetime().toMillis(), event.maxJoins(), event.audience())
                    .withLook(draft.look().withLines(new TreeMap<>(event.lines())));
            Optional<Currency> currency = terms.paid() && economy.enabled() ? economy.currency(terms.currency())
                    : Optional.empty();
            if (terms.paid() && economy.enabled() && currency.isEmpty()) {
                plugin.getLogger().warning("JoinMe costs " + terms.currency() + ", which is no currency of "
                        + "economy.currencies, it stays free");
            }
            if (currency.isEmpty()) {
                return calls.create(finished);
            }
            UUID creator = draft.spot().creator();
            return economy.withdraw(creator, currency.get(), terms.cost()).thenCompose(paid -> {
                if (!paid.success()) {
                    return CompletableFuture.completedFuture(JoinMeResult.of(
                            paid.failureReason() == EconomyResult.FailureReason.INSUFFICIENT_FUNDS
                                    ? JoinMeResult.Status.NOT_ENOUGH_MONEY : JoinMeResult.Status.FAILED));
                }
                return calls.create(finished).thenCompose(result -> {
                    if (result.success()) {
                        return CompletableFuture.completedFuture(result);
                    }
                    return economy.deposit(creator, currency.get(), terms.cost()).handle((refund, error) -> {
                        if (error != null || !refund.success()) {
                            plugin.getLogger().warning("Could not give " + terms.cost() + " " + terms.currency()
                                    + " back to " + creator + " after a refused JoinMe");
                        }
                        return result;
                    });
                });
            });
        }).thenApply(result -> {
            result.joinMe().ifPresent(joinMe -> known.put(joinMe.id(), joinMe));
            return result;
        });
    }

    private void arrival(Player carrier, JsonObject message) {
        Optional<JoinMe> joinMe = JoinMeCodec.joinMe(message.get("joinme"));
        if (joinMe.isEmpty()) {
            return;
        }
        JsonElement partyFlag = message.get("party");
        boolean party = partyFlag != null && partyFlag.isJsonPrimitive() && partyFlag.getAsBoolean();
        UUID uuid = carrier.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                place(player, joinMe.get(), party);
            }
        }, settings.delayTicks());
    }

    private void place(Player player, JoinMe joinMe, boolean party) {
        Player creator = plugin.getServer().getPlayer(joinMe.creator());
        World world = creator != null ? creator.getWorld() : plugin.getServer().getWorld(joinMe.world());
        JoinMe current = world == null ? joinMe : joinMe.withWorld(world.getName());
        JoinMeSpot spot = spot(joinMe.creator(), current.world());
        Optional<JoinMeHandler> handler = handlers.forArrival(current, spot);
        JoinMeArrival arrival = new JoinMeArrival(player.getUniqueId(), current, party);
        JoinMeArrivedEvent.Placement placement = ArrivalPlan.decide(handler.isPresent(),
                () -> arrive(handler.orElseThrow(), arrival), settings.sameWorld(), settings.position(),
                world != null, creator != null && world != null && creator.getWorld().equals(world));
        switch (placement) {
            case NEAR_CREATOR -> player.teleportAsync(creator.getLocation());
            case WORLD_SPAWN -> {
                if (!player.getWorld().equals(world)) {
                    Location spawn = world.getSpawnLocation();
                    player.teleportAsync(spawn);
                }
            }
            case REFUSED -> refused(player);
            default -> {
            }
        }
        if (placement != JoinMeArrivedEvent.Placement.REFUSED && settings.arrivalMessage()) {
            Locale target = PlayerLocales.of(player, profiles);
            boolean revealed = ViewerRules.revealsRealName(player.getUniqueId(), joinMe.creator(),
                    player.hasPermission(PlayerService.REAL_NAME_PERMISSION));
            player.sendMessage(locale.render(party ? "joinme.arrived-party" : "joinme.arrived", target,
                    Placeholder.unparsed("creator", current.name(revealed)),
                    Placeholder.unparsed("server", current.server()), Placeholder.unparsed("world", current.world())));
        }
        events.publish(new JoinMeArrivedEvent(player.getUniqueId(), current, placement,
                handler.map(JoinMeHandler::id).orElse(""), party));
    }

    private JoinMeOutcome arrive(JoinMeHandler handler, JoinMeArrival arrival) {
        try {
            return handler.arrive(arrival);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "JoinMe handler " + handler.id() + " failed for "
                    + arrival.player(), e);
            return JoinMeOutcome.FALLBACK;
        }
    }

    private void refused(Player player) {
        player.sendMessage(locale.render("joinme.arrival-refused", PlayerLocales.of(player, profiles)));
        if (settings.sendBackWhenRefused()) {
            arrivals.previousServer(player).ifPresent(previous -> connector.connect(player.getUniqueId(), previous));
        }
    }

    private JoinMeSpot spot(UUID creator, String world) {
        String group = directory.byName(config.serverId()).map(NetworkServer::group)
                .orElse(NetworkServer.GROUP_DEFAULT);
        return new JoinMeSpot(creator, config.serverId(), group, world, gameState.state());
    }

    private List<JoinMe> remember(List<JoinMe> joinMes) {
        joinMes.forEach(joinMe -> known.put(joinMe.id(), joinMe));
        return joinMes;
    }

    private <T> CompletableFuture<T> sync(Supplier<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable run = () -> {
            try {
                result.complete(task.get());
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        };
        if (plugin.getServer().isPrimaryThread()) {
            run.run();
        } else {
            try {
                plugin.getServer().getScheduler().runTask(plugin, run);
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        }
        return result;
    }

    private static JoinMeResult.Status unreachable(Throwable error) {
        return ProxyLink.noRoute(error) ? JoinMeResult.Status.UNAVAILABLE : JoinMeResult.Status.FAILED;
    }
}
