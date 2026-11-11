package net.prismarin.core.remote;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.messaging.MessageBus;
import net.prismarin.api.messaging.NetworkMessage;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.moderation.ModerationChannel;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.network.NetworkCalls;
import net.prismarin.shared.network.NetworkEvents;
import net.prismarin.shared.network.NoRouteException;
import net.prismarin.shared.network.PendingCalls;
import net.prismarin.shared.network.ProxyLink;
import net.prismarin.shared.network.Route;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

@Singleton
public final class PaperProxyLink implements ProxyLink, Listener {

    private static final long TIMEOUT_MILLIS = 5_000L;

    private final PrismarinCorePlugin plugin;
    private final ModerationChannel channel;
    private final MessageBus bus;
    private final EventBus events;
    private final CoreConfig config;
    private final PendingCalls pending = new PendingCalls(TIMEOUT_MILLIS);

    @Inject
    public PaperProxyLink(PrismarinCorePlugin plugin, ModerationChannel channel, MessageBus bus, EventBus events,
                          CoreConfig config) {
        this.plugin = plugin;
        this.channel = channel;
        this.bus = bus;
        this.events = events;
        this.config = config;
    }

    public void start() {
        channel.handleRemote(this::answer, this::event);
        if (config.redisEnabled()) {
            bus.subscribe(NetworkCalls.BUS_ANSWERS, this::busAnswer);
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public CompletableFuture<JsonObject> call(NetworkCalls.Action action, JsonObject body, UUID avoid) {
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        Runnable send = () -> {
            try {
                route(action, body, avoid).whenComplete((answer, error) -> {
                    if (error != null) {
                        result.completeExceptionally(error);
                    } else {
                        result.complete(answer);
                    }
                });
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        };
        if (plugin.getServer().isPrimaryThread()) {
            send.run();
        } else {
            try {
                plugin.getServer().getScheduler().runTask(plugin, send);
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        }
        return result;
    }

    private CompletableFuture<JsonObject> route(NetworkCalls.Action action, JsonObject body, UUID avoid) {
        List<UUID> online = plugin.getServer().getOnlinePlayers().stream().map(Player::getUniqueId).toList();
        Route route = Route.choose(online, avoid, config.redisEnabled());
        if (!route.reachable()) {
            return CompletableFuture.failedFuture(new NoRouteException());
        }
        Player carrier = route.player().map(plugin.getServer()::getPlayer).orElse(null);
        PendingCalls.Ticket ticket = pending.open(carrier == null ? PendingCalls.BUS : carrier.getUniqueId().toString());
        JsonObject message = NetworkCalls.request(ticket.id(), action, body);
        if (!NetworkCalls.fits(message)) {
            pending.fail(ticket.id(), new IllegalArgumentException("the request is too large"));
        } else if (carrier != null) {
            channel.sendSocial(carrier, message, NetworkCalls.REQUEST);
        } else {
            bus.publish(NetworkCalls.BUS_REQUESTS, message.toString());
        }
        return ticket.future();
    }

    private void answer(Player carrier, JsonObject message) {
        NetworkCalls.parseAnswer(message).ifPresent(answer ->
                pending.complete(carrier.getUniqueId().toString(), answer));
    }

    private void busAnswer(NetworkMessage message) {
        try {
            JsonObject payload = JsonParser.parseString(message.payloadJson()).getAsJsonObject();
            if (config.serverId().equals(NetworkCalls.text(payload.get("to")))) {
                NetworkCalls.parseAnswer(payload).ifPresent(answer -> pending.complete(PendingCalls.BUS, answer));
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Ignoring malformed network answer: " + e.getMessage());
        }
    }

    private void event(JsonObject message) {
        NetworkEvents.decode(message).ifPresent(events::publish);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.forget(event.getPlayer().getUniqueId().toString());
    }
}
