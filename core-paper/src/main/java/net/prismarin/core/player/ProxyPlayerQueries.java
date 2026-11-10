package net.prismarin.core.player;

import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.moderation.ModerationChannel;
import net.prismarin.shared.player.PlayerWire;
import org.bukkit.entity.Player;

@Singleton
public final class ProxyPlayerQueries {

    private static final long TIMEOUT_MILLIS = 3_000L;
    private static final long SILENCE_MILLIS = 60_000L;

    private record Pending(UUID carrier, CompletableFuture<List<PlayerWire.Live>> future) {
    }

    private final PrismarinCorePlugin plugin;
    private final ModerationChannel channel;
    private final AtomicInteger ids = new AtomicInteger();
    private final Map<Integer, Pending> pending = new ConcurrentHashMap<>();
    private volatile long silentUntil;

    @Inject
    public ProxyPlayerQueries(PrismarinCorePlugin plugin, ModerationChannel channel) {
        this.plugin = plugin;
        this.channel = channel;
    }

    public boolean reachable() {
        return System.currentTimeMillis() >= silentUntil && !plugin.getServer().getOnlinePlayers().isEmpty();
    }

    public CompletableFuture<List<PlayerWire.Live>> query(List<UUID> players, List<String> names) {
        int id = ids.incrementAndGet();
        CompletableFuture<List<PlayerWire.Live>> future = new CompletableFuture<>();
        future.orTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).whenComplete((result, error) -> {
            pending.remove(id);
            if (error instanceof TimeoutException) {
                silentUntil = System.currentTimeMillis() + SILENCE_MILLIS;
            }
        });
        JsonObject message = new PlayerWire.Query(id, players, names).toJson();
        Runnable send = () -> {
            Iterator<? extends Player> online = plugin.getServer().getOnlinePlayers().iterator();
            if (!online.hasNext()) {
                future.completeExceptionally(new IllegalStateException("no player online to carry the query"));
                return;
            }
            Player carrier = online.next();
            if (future.isDone()) {
                return;
            }
            pending.put(id, new Pending(carrier.getUniqueId(), future));
            try {
                channel.sendSocial(carrier, message, PlayerWire.QUERY);
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        };
        if (plugin.getServer().isPrimaryThread()) {
            send.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, send);
        }
        return future;
    }

    public void answer(Player carrier, JsonObject message) {
        int id = PlayerWire.id(message);
        Pending entry = pending.get(id);
        if (entry == null || !entry.carrier().equals(carrier.getUniqueId()) || !pending.remove(id, entry)) {
            return;
        }
        silentUntil = 0L;
        entry.future().complete(PlayerWire.players(message));
    }

    public void forget(UUID carrier) {
        pending.values().removeIf(entry -> {
            if (entry.carrier().equals(carrier)) {
                entry.future().completeExceptionally(new IllegalStateException("the carrier left"));
                return true;
            }
            return false;
        });
    }
}
