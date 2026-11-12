package net.prismarin.core.social;

import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import net.prismarin.api.social.SocialResult;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.moderation.ModerationChannel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@Singleton
public final class SocialClient implements Listener {

    public static final String VIEW_NONE = "none";
    public static final String VIEW_FRIENDS = "friends";
    public static final String VIEW_REQUESTS = "requests";
    public static final String VIEW_PARTY = "party";
    public static final String VIEW_PARTY_INVITE = "party-invite";
    public static final String VIEW_SETTINGS = "settings";
    public static final String VIEW_CLAN = "clan";

    private static final long TIMEOUT_SECONDS = 4L;
    private static final long HELLO_DELAY_TICKS = 30L;

    public static final class BusyException extends RuntimeException {

        BusyException() {
            super("a social request is already running", null, false, false);
        }
    }

    private record Pending(UUID player, CompletableFuture<SocialView> future) {
    }

    private record PendingQuery(UUID carrier, CompletableFuture<JsonObject> future) {
    }

    private final PrismarinCorePlugin plugin;
    private final ModerationChannel channel;
    private final PartyMirror mirror;
    private final ClanMirror clans;
    private final AtomicInteger ids = new AtomicInteger();
    private final Map<Integer, Pending> pending = new ConcurrentHashMap<>();
    private final Map<Integer, PendingQuery> queries = new ConcurrentHashMap<>();
    private final Map<UUID, SocialView.Features> features = new ConcurrentHashMap<>();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    @Inject
    public SocialClient(PrismarinCorePlugin plugin, ModerationChannel channel, PartyMirror mirror,
                        ClanMirror clans) {
        this.plugin = plugin;
        this.channel = channel;
        this.mirror = mirror;
        this.clans = clans;
    }

    public ClanMirror clans() {
        return clans;
    }

    public CompletableFuture<SocialView> clanSetting(Player player, String key, String value) {
        return request(player, "clan-setting", null, key, value, VIEW_CLAN, 0);
    }

    public SocialView.Features features(Player player) {
        return features.getOrDefault(player.getUniqueId(), SocialView.Features.NONE);
    }

    public boolean known(Player player) {
        return features.containsKey(player.getUniqueId());
    }

    public PartyMirror mirror() {
        return mirror;
    }

    public CompletableFuture<SocialView> view(Player player, String view, int page) {
        return request(player, "view", null, null, null, view, page);
    }

    public CompletableFuture<SocialView> act(Player player, String action, UUID target, String view, int page) {
        return request(player, action, target, null, null, view, page);
    }

    public CompletableFuture<SocialView> setting(Player player, String key, String value) {
        return request(player, "setting", null, key, value, VIEW_SETTINGS, 0);
    }

    public CompletableFuture<SocialView> partySetting(Player player, String key, String value) {
        JsonObject options = new JsonObject();
        options.addProperty(key, value);
        return send(player, "party-setting", null, null, null, options, VIEW_PARTY, 0, true);
    }

    public CompletableFuture<SocialView> hello(Player player) {
        return request(player, "hello", null, null, null, VIEW_NONE, 0);
    }

    public CompletableFuture<SocialView> request(Player player, String action, UUID target, String key, String value,
                                                 String view, int page) {
        return send(player, action, target, key, value, null, view, page, !"hello".equals(action));
    }

    public CompletableFuture<SocialResult> perform(Player player, String action, UUID target, JsonObject options) {
        return send(player, action, target, null, null, options, VIEW_NONE, 0, false)
                .thenApply(view -> view.result() == null ? SocialResult.FAILED : view.result())
                .exceptionally(error -> SocialResult.UNAVAILABLE);
    }

    public CompletableFuture<JsonObject> query(Player carrier, String query, UUID player, UUID other) {
        int id = ids.incrementAndGet();
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        queries.put(id, new PendingQuery(carrier.getUniqueId(), future));
        future.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((result, error) -> queries.remove(id));
        JsonObject message = new JsonObject();
        message.addProperty("id", id);
        message.addProperty("query", query);
        message.addProperty("player", player.toString());
        if (other != null) {
            message.addProperty("other", other.toString());
        }
        deliver(carrier, message, "social-query", future);
        return future;
    }

    private CompletableFuture<SocialView> send(Player player, String action, UUID target, String key, String value,
                                               JsonObject options, String view, int page, boolean gated) {
        if (gated && !busy.add(player.getUniqueId())) {
            return CompletableFuture.failedFuture(new BusyException());
        }
        boolean hello = "hello".equals(action);
        int id = ids.incrementAndGet();
        CompletableFuture<SocialView> future = new CompletableFuture<>();
        pending.put(id, new Pending(player.getUniqueId(), future));
        future.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((result, error) -> {
            pending.remove(id);
            if (gated) {
                busy.remove(player.getUniqueId());
            } else if (hello && error != null && player.isOnline()) {
                features.putIfAbsent(player.getUniqueId(), SocialView.Features.NONE);
            }
        });
        JsonObject message = new JsonObject();
        message.addProperty("id", id);
        message.addProperty("action", action);
        if (target != null) {
            message.addProperty("target", target.toString());
        }
        if (key != null) {
            message.addProperty("key", key);
        }
        if (value != null) {
            message.addProperty("value", value);
        }
        if (options != null) {
            message.add("options", options);
        }
        message.addProperty("view", view);
        message.addProperty("page", Math.max(0, page));
        deliver(player, message, "social", future);
        return future;
    }

    private void deliver(Player player, JsonObject message, String type, CompletableFuture<?> future) {
        Runnable task = () -> {
            try {
                if (!player.isOnline()) {
                    future.completeExceptionally(new IllegalStateException("player left"));
                    return;
                }
                channel.sendSocial(player, message, type);
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        };
        if (plugin.getServer().isPrimaryThread()) {
            task.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    public <T> void onMain(CompletableFuture<T> future, BiConsumer<T, Throwable> action) {
        future.whenComplete((result, error) -> plugin.getServer().getScheduler().runTask(plugin,
                () -> action.accept(result, error)));
    }

    public void receive(Player carrier, JsonObject message) {
        SocialView view = SocialView.parse(message);
        features.put(carrier.getUniqueId(), view.features());
        if (view.clanDisplay() != null) {
            clans.display(view.clanDisplay());
        }
        Optional.ofNullable(pending.remove(view.id()))
                .filter(entry -> entry.player().equals(carrier.getUniqueId()))
                .ifPresent(entry -> entry.future().complete(view));
    }

    public void answer(Player carrier, JsonObject message) {
        int id = message.has("id") && message.get("id").isJsonPrimitive() ? message.get("id").getAsInt() : 0;
        Optional.ofNullable(queries.remove(id))
                .filter(entry -> entry.carrier().equals(carrier.getUniqueId()))
                .ifPresent(entry -> entry.future().complete(message));
    }

    public void party(JsonObject message) {
        mirror.receive(message, uuid -> plugin.getServer().getPlayer(uuid) != null);
    }

    public void clan(JsonObject message) {
        clans.receive(message, uuid -> plugin.getServer().getPlayer(uuid) != null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                hello(player).exceptionally(error -> null);
            }
        }, HELLO_DELAY_TICKS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        features.remove(uuid);
        busy.remove(uuid);
        mirror.forget(uuid, other -> plugin.getServer().getPlayer(other) != null);
        clans.forget(uuid, other -> plugin.getServer().getPlayer(other) != null);
        pending.values().removeIf(entry -> {
            if (entry.player().equals(uuid)) {
                entry.future().cancel(false);
                return true;
            }
            return false;
        });
        queries.values().removeIf(entry -> {
            if (entry.carrier().equals(uuid)) {
                entry.future().cancel(false);
                return true;
            }
            return false;
        });
    }
}
