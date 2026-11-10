package net.prismarin.velocity.player;

import com.google.gson.JsonObject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import net.prismarin.api.player.LocaleChangedEvent;
import net.prismarin.api.player.NetworkPlayer;
import net.prismarin.api.player.PlayerAttributes;
import net.prismarin.api.player.PlayerService;
import net.prismarin.api.player.RankDisplay;
import net.prismarin.api.player.ServerChangedEvent;
import net.prismarin.api.player.ViewerRules;
import net.prismarin.shared.platform.CoreServices;
import net.prismarin.shared.player.PlayerSnapshots;
import net.prismarin.shared.player.PlayerWire;
import net.prismarin.velocity.admin.ModerationChannel;
import net.prismarin.velocity.admin.VanishRegistry;
import net.prismarin.velocity.locale.ProxyMessages;

public final class ProxyPlayerService implements PlayerService, ModerationChannel.PlayerHandler {

    private record Reported(String server, String world, String nick) {
    }

    private final ProxyServer proxy;
    private final CoreServices services;
    private final ProxyMessages messages;
    private final VanishRegistry vanish;
    private final ProxyNickClaims claims;
    private final ModerationChannel channel;
    private final Executor executor;
    private final Locale fallback;
    private final PlayerAttributes attributes = new PlayerAttributes();
    private final Map<UUID, Reported> reported = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> levels = new ConcurrentHashMap<>();

    public ProxyPlayerService(ProxyServer proxy, CoreServices services, ProxyMessages messages, VanishRegistry vanish,
                              ProxyNickClaims claims, ModerationChannel channel, Executor executor) {
        this.proxy = proxy;
        this.services = services;
        this.messages = messages;
        this.vanish = vanish;
        this.claims = claims;
        this.channel = channel;
        this.executor = executor;
        this.fallback = messages.fallback();
    }

    @Override
    public Optional<NetworkPlayer> online(UUID player) {
        return player == null ? Optional.empty() : proxy.getPlayer(player).map(this::snapshot);
    }

    @Override
    public Collection<NetworkPlayer> onlinePlayers() {
        List<NetworkPlayer> players = new ArrayList<>();
        for (Player player : proxy.getAllPlayers()) {
            players.add(snapshot(player));
        }
        return List.copyOf(players);
    }

    @Override
    public CompletableFuture<Optional<NetworkPlayer>> find(UUID player) {
        Optional<NetworkPlayer> here = online(player);
        if (here.isPresent() || player == null) {
            return CompletableFuture.completedFuture(here);
        }
        return services.profiles().find(player)
                .thenApply(profile -> profile.map(found -> PlayerSnapshots.offline(found, fallback)))
                .exceptionally(error -> Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<NetworkPlayer>> findByRealName(String realName) {
        if (!ViewerRules.plausibleName(realName)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String name = realName.trim();
        Optional<Player> here = proxy.getPlayer(name);
        if (here.isPresent()) {
            return CompletableFuture.completedFuture(Optional.of(snapshot(here.get())));
        }
        return services.profiles().lookup(name)
                .thenCompose(uuid -> uuid.map(this::find)
                        .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())))
                .exceptionally(error -> Optional.empty());
    }

    @Override
    public Locale locale(UUID player) {
        if (player == null) {
            return fallback;
        }
        return proxy.getPlayer(player).map(messages::localeOf).orElse(fallback);
    }

    @Override
    public boolean revealsRealName(UUID viewer, UUID target) {
        return ViewerRules.revealsRealName(viewer, target, privileged(viewer));
    }

    @Override
    public boolean canSee(UUID viewer, NetworkPlayer target) {
        return ViewerRules.canSee(viewer, target, seesVanished(viewer));
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
        return ViewerRules.resolve(onlinePlayers(), typed, reveals(viewer), sees(viewer));
    }

    @Override
    public CompletableFuture<Optional<NetworkPlayer>> resolve(UUID viewer, String typed) {
        Optional<NetworkPlayer> here = resolveOnline(viewer, typed);
        if (here.isPresent() || !ViewerRules.plausibleName(typed)) {
            return CompletableFuture.completedFuture(here);
        }
        Predicate<NetworkPlayer> reveals = reveals(viewer);
        Predicate<NetworkPlayer> sees = sees(viewer);
        String name = typed.trim();
        return services.profiles().lookup(name)
                .thenCompose(uuid -> uuid.map(this::find)
                        .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())))
                .thenApply(found -> found.flatMap(player -> ViewerRules.resolve(List.of(player), name, reveals,
                        sees)))
                .exceptionally(error -> Optional.empty());
    }

    @Override
    public List<String> suggestions(UUID viewer, String prefix) {
        return ViewerRules.suggestions(onlinePlayers(), prefix, reveals(viewer), sees(viewer));
    }

    @Override
    public PlayerAttributes attributes() {
        return attributes;
    }

    @Override
    public void state(Player carrier, String server, JsonObject message) {
        PlayerWire.State state = PlayerWire.State.fromJson(message);
        reported.put(carrier.getUniqueId(), new Reported(server, state.world(), state.nick()));
    }

    @Override
    public void query(Player carrier, JsonObject message) {
        PlayerWire.Query.fromJson(message).ifPresent(query -> CompletableFuture.supplyAsync(() -> answer(query),
                        executor)
                .thenAccept(answer -> {
                    if (carrier.isActive()) {
                        channel.sendSocial(carrier, answer);
                    }
                })
                .exceptionally(error -> null));
    }

    public void localeChanged(UUID player, Locale previous, Locale current) {
        services.events().publish(new LocaleChangedEvent(player, previous, current));
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        refreshLevel(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onServerConnected(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        String previous = event.getPreviousServer() == null ? "" : event.getPreviousServer().getServerInfo().getName();
        String current = player.getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse("");
        services.events().publish(new ServerChangedEvent(player.getUniqueId(), previous, current));
        refreshLevel(player.getUniqueId());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        reported.remove(uuid);
        levels.remove(uuid);
    }

    JsonObject answer(PlayerWire.Query query) {
        Map<UUID, PlayerWire.Live> found = new LinkedHashMap<>();
        for (UUID uuid : query.players()) {
            online(uuid).ifPresent(player -> found.put(player.uuid(), live(player)));
        }
        if (!query.names().isEmpty()) {
            for (NetworkPlayer player : onlinePlayers()) {
                if (query.names().stream().anyMatch(name -> matches(player, name))) {
                    found.putIfAbsent(player.uuid(), live(player));
                }
            }
        }
        JsonObject answer = PlayerWire.answer(query.id(), List.copyOf(found.values()));
        answer.addProperty("type", PlayerWire.ANSWER);
        return answer;
    }

    static boolean matches(NetworkPlayer player, String name) {
        return player.realName().equalsIgnoreCase(name) || player.nicked() && player.nick().equalsIgnoreCase(name);
    }

    static PlayerWire.Live live(NetworkPlayer player) {
        return new PlayerWire.Live(player.uuid(), player.realName(), player.nick(), player.server(), player.world(),
                player.locale().toLanguageTag(), player.vanished());
    }

    private NetworkPlayer snapshot(Player player) {
        UUID uuid = player.getUniqueId();
        String server = player.getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse("");
        Reported state = reported.get(uuid);
        boolean fresh = state != null && state.server().equalsIgnoreCase(server);
        String nick = fresh ? state.nick() : claims.nickOf(uuid).orElse("");
        return new NetworkPlayer(uuid, player.getUsername(), nick, messages.localeOf(player), server,
                fresh ? state.world() : "", true, vanish.vanished(uuid), RankDisplay.NONE, RankDisplay.NONE,
                levels.getOrDefault(uuid, 0), System.currentTimeMillis(), NetworkPlayer.Source.LOCAL);
    }

    private void refreshLevel(UUID player) {
        services.profiles().find(player).thenAccept(profile -> profile.ifPresent(found -> {
            if (proxy.getPlayer(player).isPresent()) {
                levels.put(player, found.getLevel());
            }
        })).exceptionally(error -> null);
    }

    private boolean privileged(UUID viewer) {
        return viewer == null || proxy.getPlayer(viewer)
                .map(player -> player.hasPermission(REAL_NAME_PERMISSION)).orElse(false);
    }

    private boolean seesVanished(UUID viewer) {
        return viewer == null || proxy.getPlayer(viewer)
                .map(player -> player.hasPermission(SEE_VANISHED_PERMISSION)).orElse(false);
    }

    private Predicate<NetworkPlayer> reveals(UUID viewer) {
        boolean privileged = privileged(viewer);
        return target -> ViewerRules.revealsRealName(viewer, target.uuid(), privileged);
    }

    private Predicate<NetworkPlayer> sees(UUID viewer) {
        boolean seesVanished = seesVanished(viewer);
        return target -> ViewerRules.canSee(viewer, target, seesVanished);
    }
}
