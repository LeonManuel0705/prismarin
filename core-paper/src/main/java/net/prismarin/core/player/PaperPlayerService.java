package net.prismarin.core.player;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import net.prismarin.api.player.NetworkPlayer;
import net.prismarin.api.player.PlayerAttributes;
import net.prismarin.api.player.PlayerService;
import net.prismarin.api.player.RankDisplay;
import net.prismarin.api.player.ViewerRules;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.nick.NickIdentity;
import net.prismarin.core.nick.NickRegistry;
import net.prismarin.core.nick.NickStore;
import net.prismarin.core.rank.RankFormatProvider;
import net.prismarin.core.social.BackendFriendService;
import net.prismarin.core.vanish.VanishService;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.platform.CoreServices;
import net.prismarin.shared.player.PlayerSnapshots;
import net.prismarin.shared.player.PlayerWire;
import net.prismarin.shared.social.FriendDocuments;
import net.prismarin.shared.storage.DocumentStore;
import org.bukkit.entity.Player;

@Singleton
public final class PaperPlayerService implements PlayerService {

    private static final long PROFILE_TIMEOUT_SECONDS = 5L;

    private record Viewer(UUID uuid, boolean privileged, boolean seesVanished, Set<UUID> hiddenHere) {

        boolean reveals(NetworkPlayer target) {
            return ViewerRules.revealsRealName(uuid, target.uuid(), privileged);
        }

        boolean sees(NetworkPlayer target) {
            if (hiddenHere.contains(target.uuid())) {
                return false;
            }
            return target.source() == NetworkPlayer.Source.LOCAL || ViewerRules.canSee(uuid, target, seesVanished);
        }
    }

    private final PrismarinCorePlugin plugin;
    private final ProfileService profiles;
    private final CoreConfig config;
    private final NickRegistry nicks;
    private final NickStore nickStore;
    private final VanishService vanish;
    private final RankFormatProvider ranks;
    private final RealRanks realRanks;
    private final ProxyPlayerQueries proxy;
    private final DocumentStore documents;
    private final ExecutorService executor;
    private final Locale fallback;
    private final boolean sharedStorage;
    private final PlayerAttributes attributes = new PlayerAttributes();

    @Inject
    public PaperPlayerService(PrismarinCorePlugin plugin, ProfileService profiles, CoreConfig config,
                              NickRegistry nicks, NickStore nickStore, VanishService vanish, RankFormatProvider ranks,
                              RealRanks realRanks, ProxyPlayerQueries proxy, CoreServices services,
                              ExecutorService executor) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.config = config;
        this.nicks = nicks;
        this.nickStore = nickStore;
        this.vanish = vanish;
        this.ranks = ranks;
        this.realRanks = realRanks;
        this.proxy = proxy;
        this.documents = services.documents();
        this.executor = executor;
        this.fallback = Locale.forLanguageTag(config.defaultLocale());
        this.sharedStorage = BackendFriendService.sharedStorage(config.storageMode());
    }

    @Override
    public Optional<NetworkPlayer> online(UUID player) {
        Player online = player == null ? null : plugin.getServer().getPlayer(player);
        return online == null ? Optional.empty() : Optional.of(snapshot(online, true));
    }

    @Override
    public Collection<NetworkPlayer> onlinePlayers() {
        return snapshots(true);
    }

    @Override
    public CompletableFuture<Optional<NetworkPlayer>> find(UUID player) {
        Optional<NetworkPlayer> here = online(player);
        if (here.isPresent()) {
            return CompletableFuture.completedFuture(here);
        }
        return remote(List.of(player)).thenApply(found -> found.stream().findFirst());
    }

    @Override
    public CompletableFuture<List<NetworkPlayer>> findAll(Collection<UUID> players) {
        Map<UUID, NetworkPlayer> here = new LinkedHashMap<>();
        List<UUID> elsewhere = new ArrayList<>();
        for (UUID uuid : players.stream().distinct().toList()) {
            online(uuid).ifPresentOrElse(found -> here.put(uuid, found), () -> elsewhere.add(uuid));
        }
        return remote(elsewhere).thenApply(found -> {
            Map<UUID, NetworkPlayer> all = new LinkedHashMap<>(here);
            found.forEach(player -> all.put(player.uuid(), player));
            List<NetworkPlayer> ordered = new ArrayList<>();
            players.stream().distinct().map(all::get).filter(Objects::nonNull).forEach(ordered::add);
            return List.copyOf(ordered);
        });
    }

    @Override
    public CompletableFuture<Optional<NetworkPlayer>> findByRealName(String realName) {
        if (!ViewerRules.plausibleName(realName)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String name = realName.trim();
        Optional<NetworkPlayer> here = snapshots(false).stream()
                .filter(player -> player.realName().equalsIgnoreCase(name))
                .findFirst();
        if (here.isPresent()) {
            return CompletableFuture.completedFuture(online(here.get().uuid()));
        }
        return profiles.lookup(name).thenCompose(uuid -> uuid.map(this::find)
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())));
    }

    @Override
    public Locale locale(UUID player) {
        if (player == null) {
            return fallback;
        }
        Player online = plugin.getServer().getPlayer(player);
        if (online != null) {
            return PlayerLocales.of(online, profiles);
        }
        return profiles.getCached(player).map(profile -> PlayerSnapshots.locale(profile, fallback)).orElse(fallback);
    }

    @Override
    public boolean revealsRealName(UUID viewer, UUID target) {
        return ViewerRules.revealsRealName(viewer, target, privileged(viewer));
    }

    @Override
    public boolean canSee(UUID viewer, NetworkPlayer target) {
        if (viewer == null) {
            return true;
        }
        Player watcher = plugin.getServer().getPlayer(viewer);
        Player watched = plugin.getServer().getPlayer(target.uuid());
        if (watcher != null && watched != null) {
            return vanish.canSee(watcher, watched);
        }
        return ViewerRules.canSee(viewer, target,
                watcher != null && watcher.hasPermission(SEE_VANISHED_PERMISSION));
    }

    @Override
    public String displayName(UUID viewer, UUID target) {
        Player online = target == null ? null : plugin.getServer().getPlayer(target);
        if (online == null) {
            return "";
        }
        Optional<NickRegistry.Shown> shown = nicks.shown(target);
        if (shown.isEmpty()) {
            return online.getName();
        }
        return revealsRealName(viewer, target) ? shown.get().realName() : shown.get().nick();
    }

    @Override
    public RankDisplay rank(UUID viewer, UUID target) {
        Player online = target == null ? null : plugin.getServer().getPlayer(target);
        if (online == null) {
            return RankDisplay.NONE;
        }
        if (nicks.nicked(target) && revealsRealName(viewer, target)) {
            return realRanks.display(online);
        }
        return RealRanks.display(ranks, online);
    }

    @Override
    public Optional<NetworkPlayer> resolveOnline(UUID viewer, String typed) {
        Viewer context = viewer(viewer);
        return complete(ViewerRules.resolve(snapshots(false), typed, context::reveals, context::sees));
    }

    @Override
    public CompletableFuture<Optional<NetworkPlayer>> resolve(UUID viewer, String typed) {
        Viewer context = viewer(viewer);
        List<NetworkPlayer> here = snapshots(false);
        Optional<NetworkPlayer> local = ViewerRules.resolve(here, typed, context::reveals, context::sees);
        if (local.isPresent() && local.get().online() || !ViewerRules.plausibleName(typed)) {
            return CompletableFuture.completedFuture(complete(local));
        }
        String name = typed.trim();
        return live(List.of(), List.of(name)).thenApplyAsync(entries -> {
            Map<UUID, NetworkPlayer> candidates = new LinkedHashMap<>();
            here.forEach(player -> candidates.put(player.uuid(), player));
            if (entries != null) {
                for (PlayerWire.Live entry : entries) {
                    candidates.putIfAbsent(entry.uuid(), PlayerSnapshots.live(entry, profile(entry.uuid()), fallback,
                            System.currentTimeMillis(), NetworkPlayer.Source.PROXY));
                }
            } else {
                nickStore.claim(name).map(NickStore.Claim::owner)
                        .filter(owner -> !candidates.containsKey(owner))
                        .flatMap(this::stored)
                        .ifPresent(player -> candidates.put(player.uuid(), player));
            }
            lookup(name).filter(owner -> !candidates.containsKey(owner))
                    .flatMap(owner -> entries != null ? profile(owner).map(this::offline) : stored(owner))
                    .ifPresent(player -> candidates.put(player.uuid(), player));
            return complete(ViewerRules.resolve(candidates.values(), name, context::reveals, context::sees));
        }, executor);
    }

    @Override
    public List<String> suggestions(UUID viewer, String prefix) {
        Viewer context = viewer(viewer);
        return ViewerRules.suggestions(snapshots(false), prefix, context::reveals, context::sees);
    }

    @Override
    public PlayerAttributes attributes() {
        return attributes;
    }

    private CompletableFuture<List<NetworkPlayer>> remote(List<UUID> players) {
        if (players.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return live(players, List.of()).thenApplyAsync(entries -> {
            Map<UUID, PlayerWire.Live> byUuid = new LinkedHashMap<>();
            if (entries != null) {
                entries.forEach(entry -> byUuid.put(entry.uuid(), entry));
            }
            List<NetworkPlayer> found = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (UUID uuid : players) {
                Optional<NetworkPlayer> player;
                if (entries == null) {
                    player = stored(uuid);
                } else if (byUuid.containsKey(uuid)) {
                    player = Optional.of(PlayerSnapshots.live(byUuid.get(uuid), profile(uuid), fallback, now,
                            NetworkPlayer.Source.PROXY));
                } else {
                    player = profile(uuid).map(this::offline);
                }
                player.ifPresent(found::add);
            }
            return List.copyOf(found);
        }, executor);
    }

    private CompletableFuture<List<PlayerWire.Live>> live(List<UUID> players, List<String> names) {
        if (!proxy.reachable()) {
            return CompletableFuture.completedFuture(null);
        }
        return proxy.query(players, names).exceptionally(error -> null);
    }

    private Optional<NetworkPlayer> stored(UUID player) {
        Optional<PlayerSnapshots.StoredPresence> presence = sharedStorage
                ? documents.find(FriendDocuments.PRESENCE, player.toString())
                .flatMap(PlayerSnapshots.StoredPresence::fromJson)
                : Optional.empty();
        Optional<String> nick = presence.filter(PlayerSnapshots.StoredPresence::online).isPresent()
                ? nickStore.owner(player).map(NickIdentity::name)
                : Optional.empty();
        return PlayerSnapshots.stored(player, presence, nick, profile(player), fallback);
    }

    private NetworkPlayer offline(PlayerProfile profile) {
        return PlayerSnapshots.offline(profile, fallback);
    }

    private Optional<PlayerProfile> profile(UUID player) {
        try {
            return profiles.find(player).get(PROFILE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<UUID> lookup(String name) {
        try {
            return profiles.lookup(name).get(PROFILE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<NetworkPlayer> complete(Optional<NetworkPlayer> match) {
        return match.map(found -> {
            if (found.source() != NetworkPlayer.Source.LOCAL) {
                return found;
            }
            Player online = plugin.getServer().getPlayer(found.uuid());
            if (online == null) {
                return found;
            }
            NetworkPlayer full = snapshot(online, true);
            return found.online() ? full : full.asOffline();
        });
    }

    private Viewer viewer(UUID viewer) {
        if (viewer == null) {
            return new Viewer(null, true, true, Set.of());
        }
        Player watcher = plugin.getServer().getPlayer(viewer);
        Set<UUID> hidden = new HashSet<>();
        for (Player target : plugin.getServer().getOnlinePlayers()) {
            boolean visible = watcher != null ? vanish.canSee(watcher, target) : !vanish.vanished(target);
            if (!visible) {
                hidden.add(target.getUniqueId());
            }
        }
        boolean privileged = watcher != null && watcher.hasPermission(REAL_NAME_PERMISSION);
        boolean seesVanished = watcher != null && watcher.hasPermission(SEE_VANISHED_PERMISSION);
        return new Viewer(viewer, privileged, seesVanished, Set.copyOf(hidden));
    }

    private boolean privileged(UUID viewer) {
        if (viewer == null) {
            return true;
        }
        Player watcher = plugin.getServer().getPlayer(viewer);
        return watcher != null && watcher.hasPermission(REAL_NAME_PERMISSION);
    }

    private List<NetworkPlayer> snapshots(boolean withRanks) {
        List<NetworkPlayer> players = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            players.add(snapshot(player, withRanks));
        }
        return List.copyOf(players);
    }

    private NetworkPlayer snapshot(Player player, boolean withRanks) {
        UUID uuid = player.getUniqueId();
        Optional<NickRegistry.Shown> shown = nicks.shown(uuid);
        String realName = shown.map(NickRegistry.Shown::realName).orElse(player.getName());
        RankDisplay rank = withRanks ? RealRanks.display(ranks, player) : RankDisplay.NONE;
        RankDisplay realRank = withRanks && shown.isPresent() ? realRanks.display(player) : rank;
        int level = profiles.getCached(uuid).map(PlayerProfile::getLevel).orElse(0);
        return new NetworkPlayer(uuid, realName, shown.map(NickRegistry.Shown::nick).orElse(""),
                PlayerLocales.of(player, profiles), config.serverId(), player.getWorld().getName(), true,
                vanish.vanished(player), rank, realRank, level, System.currentTimeMillis(),
                NetworkPlayer.Source.LOCAL);
    }
}
