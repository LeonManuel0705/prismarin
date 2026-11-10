package net.prismarin.core.nick;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.nick.NickChangedEvent;
import net.prismarin.api.nick.NickResult;
import net.prismarin.api.nick.NickService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.server.GameState;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.core.CoreModules;
import net.prismarin.core.ModuleState;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.tablist.TablistManager;
import net.prismarin.core.vanish.VanishService;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.status.SimpleGameStateService;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.scheduler.BukkitTask;

@Singleton
public final class NickManager implements NickService {

    public static final String PERMISSION_USE = "prismarin.nick.use";
    public static final String PERMISSION_CUSTOM = "prismarin.nick.custom";
    public static final String PERMISSION_AUTO = "prismarin.nick.auto";
    public static final String PERMISSION_OTHERS = "prismarin.nick.others";
    public static final String PERMISSION_REALNAME = "prismarin.nick.realname";
    public static final List<String> NICK_PERMISSIONS = List.of(PERMISSION_USE, PERMISSION_CUSTOM, PERMISSION_AUTO);

    private static final long WATCH_INTERVAL_TICKS = 10L;
    private static final long REMINDER_INTERVAL_TICKS = 20L;
    private static final long ACTION_BAR_VISIBLE_MILLIS = 2_000L;
    private static final int GENERATED_ATTEMPTS = 40;
    private static final long LOGIN_TTL_MILLIS = 15L * 60L * 1000L;
    private static final long RENICK_TIMEOUT_SECONDS = 5L;

    private final PrismarinCorePlugin plugin;
    private final NickSettings settings;
    private final NickStore store;
    private final SkinPool skins;
    private final NickRegistry registry;
    private final NickPermissions permissions;
    private final FakeRankFormat fakeRank;
    private final ProfileService profiles;
    private final LocaleService locale;
    private final EventBus events;
    private final ExecutorService executor;
    private final SimpleGameStateService gameState;
    private final ServerDirectory directory;
    private final CoreConfig config;
    private final ModuleState modules;
    private final TablistManager tablist;
    private final VanishService vanish;
    private final NickNames names;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, LoginState> logins = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> roundMarkers = new ConcurrentHashMap<>();
    private volatile boolean running;
    private volatile String group = NetworkServer.GROUP_DEFAULT;
    private PlayerNameIndex nameIndex;
    private BukkitTask watcher;
    private BukkitTask reminder;
    private final Map<UUID, Reminder> reminders = new ConcurrentHashMap<>();

    @Inject
    public NickManager(PrismarinCorePlugin plugin, NickSettings settings, NickStore store, SkinPool skins,
                       NickRegistry registry, NickPermissions permissions, FakeRankFormat fakeRank,
                       ProfileService profiles, LocaleService locale, EventBus events, ExecutorService executor,
                       SimpleGameStateService gameState, ServerDirectory directory, CoreConfig config,
                       ModuleState modules, TablistManager tablist, VanishService vanish) {
        this.plugin = plugin;
        this.settings = settings;
        this.store = store;
        this.skins = skins;
        this.registry = registry;
        this.permissions = permissions;
        this.fakeRank = fakeRank;
        this.profiles = profiles;
        this.locale = locale;
        this.events = events;
        this.executor = executor;
        this.gameState = gameState;
        this.directory = directory;
        this.config = config;
        this.modules = modules;
        this.tablist = tablist;
        this.vanish = vanish;
        this.names = new NickNames(settings.names().list(), settings.names().generator(), settings.names().blocked());
    }

    public void start() {
        group = directory.byName(config.serverId()).map(NetworkServer::group).orElse(NetworkServer.GROUP_DEFAULT);
        nameIndex = PlayerNameIndex.locate(plugin.getServer(), plugin.getLogger());
        running = true;
        settings.problems().forEach(problem -> plugin.getLogger().warning(NickSettings.FILE_NAME + ": " + problem));
        skins.start();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            handleJoin(player);
        }
        watcher = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::watch, WATCH_INTERVAL_TICKS, WATCH_INTERVAL_TICKS);
        if (settings.actionBar()) {
            reminder = plugin.getServer().getScheduler()
                    .runTaskTimer(plugin, this::remind, REMINDER_INTERVAL_TICKS, REMINDER_INTERVAL_TICKS);
        }
        plugin.getLogger().info("Nicks on '" + config.serverId() + "' (group " + group + "): where "
                + settings.policy().where().key() + ", when " + settings.policy().when().key() + ", auto nick "
                + (settings.policy().autoNick() ? "on" : "off") + ", shown here now: " + nicksShownHere());
    }

    public void stop() {
        running = false;
        for (BukkitTask task : new BukkitTask[]{watcher, reminder}) {
            if (task != null) {
                task.cancel();
            }
        }
        watcher = null;
        reminder = null;
        skins.stop();
        permissions.undisguiseAll();
    }

    public boolean running() {
        return running;
    }

    public boolean autoNickUsedHere() {
        return settings.policy().autoNick();
    }

    void handlePreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        logins.remove(uuid);
        if (!running) {
            return;
        }
        Optional<Map<String, String>> stored = settingsOf(uuid);
        if (stored.isEmpty()) {
            return;
        }
        Map<String, String> profileSettings = stored.get();
        String realName = profiles.getCached(uuid).map(profile -> profile.getName()).orElse(event.getName());
        List<ProfileProperty> realProperties = List.copyOf(event.getPlayerProfile().getProperties());
        Optional<UUID> holder = registry.byNick(realName).filter(owner -> !owner.equals(uuid));
        if (holder.isPresent() && !freeNameBeforeLogin(holder.get(), realName)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    locale.render("nick.login-retry", Locale.forLanguageTag(config.defaultLocale())));
            return;
        }
        long now = System.currentTimeMillis();
        Optional<NickIdentity> identity = store.resolve(uuid, profileSettings, now, claimExpiry());
        Conflict conflict = identity.map(found -> conflict(found.name(), uuid, now)).orElse(Conflict.FREE);
        if (conflict == Conflict.LASTING) {
            store.release(uuid, identity.get().name());
            identity = Optional.empty();
        }
        boolean allowedHere = permissions.mayAtLogin(uuid, NICK_PERMISSIONS);
        boolean autoWanted = Boolean.parseBoolean(profileSettings.get(SETTING_AUTO))
                && permissions.mayAtLogin(uuid, List.of(PERMISSION_AUTO));
        NickPolicy.Action action = allowedHere && conflict != Conflict.TRANSIENT
                ? settings.policy().decide(NickPolicy.Moment.JOIN, nicksShownHere(), inRound(uuid),
                identity.isPresent(), autoWanted)
                : NickPolicy.Action.REAL;
        if (action == NickPolicy.Action.GENERATE) {
            identity = generate(uuid, now, null, autoKind());
        }
        NickIdentity shown = action == NickPolicy.Action.REAL ? null : identity.orElse(null);
        logins.put(uuid, new LoginState(realName, realProperties, identity.orElse(null), shown, now));
        if (mirror(profileSettings, identity.orElse(null))) {
            profiles.getCached(uuid).ifPresent(profiles::save);
        }
        if (shown != null) {
            event.setPlayerProfile(profileOf(uuid, shown.name(),
                    disguisedProperties(shown, skinFor(shown), realProperties)));
        }
        identity.ifPresent(found -> touchQuietly(uuid, found, now));
    }

    void discardLogin(UUID uuid) {
        logins.remove(uuid);
    }

    void handleJoin(Player player) {
        if (!running) {
            return;
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        logins.values().removeIf(state -> now - state.created() > LOGIN_TTL_MILLIS);
        LoginState login = logins.remove(uuid);
        String realName = login != null ? login.realName()
                : profiles.getCached(uuid).map(profile -> profile.getName()).orElse(player.getName());
        List<ProfileProperty> realProperties = login != null ? login.realProperties()
                : List.copyOf(player.getPlayerProfile().getProperties());
        Session session = new Session(uuid, realName, player.getName(), realProperties);
        session.identity = login != null ? login.identity()
                : settingsOf(uuid).flatMap(NickManager::fromSettings).orElse(null);
        session.inRound = inRound(uuid);
        session.denied = NICK_PERMISSIONS.stream().noneMatch(player::hasPermission);
        sessions.put(uuid, session);
        if (login != null && login.shown() != null) {
            session.shown = login.shown();
            registry.put(uuid, new NickRegistry.Shown(login.shown().name(), realName, login.shown().skin()));
            disguisePermissions(uuid);
            events.publish(new NickChangedEvent(uuid, realName, realName, login.shown().name(), true));
        }
        if (session.denied && session.shown != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> revealDenied(player, session));
        }
    }

    void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        Session session = sessions.remove(uuid);
        roundMarkers.remove(uuid);
        registry.remove(uuid);
        permissions.undisguise(uuid);
        if (session != null && (nameIndex == null || !nameIndex.available())
                && !player.getName().equals(session.loginName)) {
            restoreLoginName(player, session.loginName);
        }
    }

    @Override
    public boolean isNicked(UUID player) {
        return registry.nicked(player);
    }

    @Override
    public Optional<String> shownNick(UUID player) {
        return registry.shown(player).map(NickRegistry.Shown::nick);
    }

    @Override
    public Optional<String> storedNick(UUID player) {
        Session session = sessions.get(player);
        if (session != null) {
            return Optional.ofNullable(session.identity).map(NickIdentity::name);
        }
        return settingsOf(player).flatMap(NickManager::fromSettings).map(NickIdentity::name);
    }

    @Override
    public Optional<String> realName(UUID player) {
        Session session = sessions.get(player);
        if (session != null) {
            return Optional.of(session.realName);
        }
        return profiles.getCached(player).map(profile -> profile.getName());
    }

    @Override
    public Optional<UUID> findByNick(String nick) {
        return registry.byNick(nick);
    }

    @Override
    public Collection<UUID> nickedPlayers() {
        return registry.all().keySet();
    }

    @Override
    public CompletableFuture<Optional<UUID>> lookupNick(String nick) {
        Optional<UUID> online = registry.byNick(nick);
        if (online.isPresent()) {
            return CompletableFuture.completedFuture(online);
        }
        return lookupClaim(nick).thenApply(claim -> claim.map(NickStore.Claim::owner));
    }

    public CompletableFuture<Optional<NickStore.Claim>> lookupClaim(String nick) {
        if (!NickNames.validFormat(nick)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        long now = System.currentTimeMillis();
        return CompletableFuture.supplyAsync(() -> store.claim(nick)
                .filter(claim -> !claim.expired(now, claimExpiry())), executor);
    }

    @Override
    public CompletableFuture<NickResult> nickRandom(UUID player) {
        return change(player, null, false);
    }

    @Override
    public CompletableFuture<NickResult> nick(UUID player, String name) {
        if (!names.allowed(name)) {
            return CompletableFuture.completedFuture(NickResult.of(NickResult.Status.INVALID_NAME, name));
        }
        return change(player, name, false);
    }

    @Override
    public CompletableFuture<NickResult> unnick(UUID player) {
        if (!running) {
            return CompletableFuture.completedFuture(NickResult.of(NickResult.Status.DISABLED));
        }
        Session session = sessions.get(player);
        if (session == null) {
            return CompletableFuture.completedFuture(NickResult.of(NickResult.Status.NOT_ONLINE));
        }
        NickIdentity identity = session.identity;
        if (identity == null && session.shown == null) {
            return CompletableFuture.completedFuture(NickResult.of(NickResult.Status.NOT_NICKED, session.realName));
        }
        if (!session.busy.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(NickResult.of(NickResult.Status.BUSY));
        }
        CompletableFuture<NickResult> result = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            if (identity != null) {
                store.release(player, identity.name());
            }
            profiles.modify(player, profile -> mirror(profile.getSettings(), null)).join();
        }, executor).whenComplete((unused, error) -> onMain(() -> {
            session.busy.set(false);
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Could not remove the nick of " + session.realName, error);
                result.complete(NickResult.of(NickResult.Status.FAILED));
                return;
            }
            if (sessions.get(player) != session) {
                result.complete(NickResult.of(NickResult.Status.REMOVED, session.realName));
                return;
            }
            session.identity = null;
            session.optedOut = true;
            Player online = plugin.getServer().getPlayer(player);
            if (online != null && session.shown != null && canRefresh(online)) {
                reveal(online, session);
            }
            result.complete(NickResult.of(NickResult.Status.REMOVED, session.realName));
        }));
        return result;
    }

    @Override
    public boolean autoNick(UUID player) {
        return settingsOf(player).map(stored -> Boolean.parseBoolean(stored.get(SETTING_AUTO))).orElse(false);
    }

    @Override
    public CompletableFuture<Void> setAutoNick(UUID player, boolean enabled) {
        return profiles.modify(player, profile -> {
            if (enabled) {
                profile.getSettings().put(SETTING_AUTO, "true");
            } else {
                profile.getSettings().remove(SETTING_AUTO);
            }
        }).thenAccept(profile -> {
            Session session = sessions.get(player);
            if (session != null && enabled) {
                session.optedOut = false;
            }
        });
    }

    @Override
    public void markInRound(UUID player, boolean inRound) {
        roundMarkers.put(player, inRound);
        reconcileSoon(player);
    }

    @Override
    public void clearRoundMarker(UUID player) {
        if (roundMarkers.remove(player) != null) {
            reconcileSoon(player);
        }
    }

    @Override
    public boolean inRound(UUID player) {
        Boolean marker = roundMarkers.get(player);
        if (marker != null) {
            return marker;
        }
        return settings.roundStates().contains(gameState.state());
    }

    @Override
    public boolean nicksShownHere() {
        boolean reportsRounds = gameState.state() != GameState.UNKNOWN || !roundMarkers.isEmpty();
        return settings.policy().serverAllows(group, reportsRounds);
    }

    private boolean worldAllows(Player player) {
        return settings.worlds().allows(player.getWorld().getName());
    }

    private boolean shownFor(Player player) {
        return nicksShownHere() && worldAllows(player);
    }

    public Optional<Player> findOnline(String name) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return Optional.of(player);
            }
        }
        for (Session session : sessions.values()) {
            if (session.realName.equalsIgnoreCase(name)) {
                return Optional.ofNullable(plugin.getServer().getPlayer(session.uuid));
            }
        }
        return Optional.empty();
    }

    private CompletableFuture<NickResult> change(UUID player, String requested, boolean keepKind) {
        if (!running) {
            return CompletableFuture.completedFuture(NickResult.of(NickResult.Status.DISABLED));
        }
        Session session = sessions.get(player);
        if (session == null) {
            return CompletableFuture.completedFuture(NickResult.of(NickResult.Status.NOT_ONLINE));
        }
        if (!session.busy.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(NickResult.of(NickResult.Status.BUSY));
        }
        NickIdentity previous = session.identity;
        long now = System.currentTimeMillis();
        CompletableFuture<NickResult> result = new CompletableFuture<>();
        CompletableFuture.supplyAsync(() -> prepare(player, requested, previous, now, keepKind), executor)
                .whenComplete((prepared, error) -> onMain(() -> {
                    session.busy.set(false);
                    if (error != null) {
                        plugin.getLogger().log(Level.WARNING, "Could not change the nick of " + session.realName,
                                error);
                        result.complete(NickResult.of(NickResult.Status.FAILED));
                        return;
                    }
                    if (prepared.failure() != null) {
                        result.complete(prepared.failure());
                        return;
                    }
                    result.complete(adopt(player, session, prepared));
                }));
        return result;
    }

    private Prepared prepare(UUID player, String requested, NickIdentity previous, long now, boolean keepKind) {
        NickIdentity identity;
        if (requested == null) {
            NickIdentity.Kind kind = keepKind && previous != null ? previous.kind() : NickIdentity.Kind.MANUAL;
            Optional<NickIdentity> generated = generate(player, now, previous == null ? null : previous.name(), kind);
            if (generated.isEmpty()) {
                return Prepared.failed(NickResult.of(NickResult.Status.NO_FREE_NAME));
            }
            identity = generated.get();
        } else {
            if (!nameFree(requested, player, now)) {
                return Prepared.failed(NickResult.of(NickResult.Status.NAME_TAKEN, requested));
            }
            String skin = previous != null && previous.sameName(requested) ? previous.skin()
                    : skins.chooseSkin(ThreadLocalRandom.current());
            identity = new NickIdentity(requested, skin, NickIdentity.Kind.MANUAL);
            if (!store.claim(player, identity, config.serverId(), now, claimExpiry())) {
                return Prepared.failed(NickResult.of(NickResult.Status.NAME_TAKEN, requested));
            }
        }
        if (previous != null && !previous.sameName(identity.name())) {
            store.releaseClaim(player, previous.name());
        }
        profiles.modify(player, profile -> mirror(profile.getSettings(), identity)).join();
        return new Prepared(null, identity, skinFor(identity));
    }

    private NickResult adopt(UUID uuid, Session session, Prepared prepared) {
        NickIdentity identity = prepared.identity();
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || sessions.get(uuid) != session) {
            return NickResult.of(NickResult.Status.STORED, identity.name());
        }
        session.identity = identity;
        session.optedOut = false;
        session.denied = false;
        if (!settings.policy().shows(shownFor(player), inRound(uuid))) {
            if (session.shown != null && canRefresh(player)) {
                reveal(player, session);
            }
            return NickResult.of(NickResult.Status.STORED, identity.name());
        }
        if (!canRefresh(player)) {
            return NickResult.of(NickResult.Status.APPLIED, identity.name());
        }
        return show(player, session, identity, prepared.skin())
                ? NickResult.of(NickResult.Status.APPLIED, identity.name())
                : NickResult.of(NickResult.Status.FAILED);
    }

    private void watch() {
        if (!logins.isEmpty()) {
            long now = System.currentTimeMillis();
            logins.values().removeIf(state -> now - state.created() > LOGIN_TTL_MILLIS);
        }
        if (sessions.isEmpty()) {
            return;
        }
        boolean allows = nicksShownHere();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Session session = sessions.get(player.getUniqueId());
            if (session != null) {
                reconcile(player, session, allows && worldAllows(player));
            }
        }
    }

    private void reconcileSoon(UUID uuid) {
        if (plugin.getServer().isPrimaryThread()) {
            reconcileNow(uuid);
        } else {
            onMain(() -> reconcileNow(uuid));
        }
    }

    private void reconcileNow(UUID uuid) {
        Player player = plugin.getServer().getPlayer(uuid);
        Session session = sessions.get(uuid);
        if (player != null && session != null) {
            reconcile(player, session, shownFor(player));
        }
    }

    private void reconcile(Player player, Session session, boolean allows) {
        if (session.busy.get() || session.broken) {
            return;
        }
        boolean inRound = inRound(session.uuid);
        boolean started = inRound && !session.inRound;
        session.inRound = inRound;
        if (inRound && session.shown != null) {
            session.roundShown = session.shown;
        }
        if (session.denied) {
            if (session.shown != null && canRefresh(player)) {
                reveal(player, session);
            }
            return;
        }
        NickIdentity identity = session.identity;
        NickPolicy.Moment moment = started ? NickPolicy.Moment.ROUND_START : NickPolicy.Moment.CHECK;
        boolean autoWanted = started && identity == null && !session.optedOut && autoNick(session.uuid)
                && player.hasPermission(PERMISSION_AUTO);
        switch (settings.policy().decide(moment, allows, inRound, identity != null, autoWanted)) {
            case NICK -> {
                if (!identity.equals(session.shown) && canRefresh(player)
                        && !usedByOtherSession(identity.name(), session.uuid)) {
                    showWhenReady(player, session, identity, started);
                }
            }
            case GENERATE -> generateAndShow(player, session);
            case REAL -> endNick(player, session, identity, inRound);
        }
    }

    private void endNick(Player player, Session session, NickIdentity identity, boolean inRound) {
        boolean roundOver = settings.policy().when() == NickPolicy.When.IN_ROUND && !inRound;
        if (session.shown != null && canRefresh(player)) {
            reveal(player, session);
            if (roundOver) {
                send(player, "nick.revealed", Placeholder.unparsed("name", session.realName));
            }
        }
        if (roundOver && session.shown == null && identity != null && identity.roundScoped()
                && identity.equals(session.roundShown)) {
            session.roundShown = null;
            forgetAutoNick(session, identity);
        }
    }

    private void forgetAutoNick(Session session, NickIdentity identity) {
        if (!session.busy.compareAndSet(false, true)) {
            return;
        }
        UUID uuid = session.uuid;
        CompletableFuture.runAsync(() -> {
            store.release(uuid, identity.name());
            profiles.modify(uuid, profile -> mirror(profile.getSettings(), null)).join();
        }, executor).whenComplete((unused, error) -> onMain(() -> {
            session.busy.set(false);
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Could not release the auto nick of " + session.realName, error);
                return;
            }
            if (identity.equals(session.identity)) {
                session.identity = null;
            }
        }));
    }

    private void showWhenReady(Player player, Session session, NickIdentity identity, boolean announce) {
        if (!identity.poolSkin() || skins.cached(identity.skin()).isPresent()) {
            showAndAnnounce(player, session, identity, skinFor(identity), announce);
            return;
        }
        if (!session.busy.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.supplyAsync(() -> skinFor(identity), executor)
                .whenComplete((skin, error) -> onMain(() -> {
                    session.busy.set(false);
                    if (player.isOnline() && !session.denied && identity.equals(session.identity)
                            && canRefresh(player)) {
                        showAndAnnounce(player, session, identity, skin == null ? Optional.empty() : skin, announce);
                    }
                }));
    }

    private void showAndAnnounce(Player player, Session session, NickIdentity identity,
                                 Optional<NickStore.StoredSkin> skin, boolean announce) {
        if (show(player, session, identity, skin) && announce) {
            send(player, "nick.applied", Placeholder.unparsed("nick", identity.name()));
        }
    }

    private void generateAndShow(Player player, Session session) {
        if (!session.busy.compareAndSet(false, true)) {
            return;
        }
        UUID uuid = session.uuid;
        long now = System.currentTimeMillis();
        CompletableFuture.supplyAsync(() -> {
            Optional<NickIdentity> generated = generate(uuid, now, null, autoKind());
            generated.ifPresent(identity -> profiles.modify(uuid, profile -> mirror(profile.getSettings(), identity))
                    .join());
            return generated.map(identity -> new Prepared(null, identity, skinFor(identity)));
        }, executor).whenComplete((prepared, error) -> onMain(() -> {
            session.busy.set(false);
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Could not create an auto nick for " + session.realName, error);
                return;
            }
            if (prepared.isEmpty() || !player.isOnline() || sessions.get(uuid) != session) {
                return;
            }
            session.identity = prepared.get().identity();
            if (!session.denied && canRefresh(player)) {
                showAndAnnounce(player, session, session.identity, prepared.get().skin(), true);
            }
        }));
    }

    private boolean freeNameBeforeLogin(UUID owner, String takenName) {
        CompletableFuture<Boolean> freed = new CompletableFuture<>();
        onMain(() -> {
            Player player = plugin.getServer().getPlayer(owner);
            if (player == null || !player.getName().equalsIgnoreCase(takenName)) {
                freed.complete(true);
                return;
            }
            change(owner, null, true).whenComplete((result, error) -> {
                boolean clear = forceRename(owner, takenName);
                if (clear && result != null && result.success() && player.isOnline()
                        && player.getName().equals(result.name())) {
                    send(player, "nick.renamed", Placeholder.unparsed("old", takenName),
                            Placeholder.unparsed("nick", result.name()));
                }
                freed.complete(clear);
            });
        });
        try {
            return freed.get(RENICK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            plugin.getLogger().log(Level.WARNING, "Could not free the name " + takenName + " in time", e);
            return false;
        }
    }

    private boolean forceRename(UUID owner, String takenName) {
        Player player = plugin.getServer().getPlayer(owner);
        if (player == null || !player.getName().equalsIgnoreCase(takenName)) {
            return true;
        }
        Session session = sessions.get(owner);
        if (session == null || session.busy.get()) {
            return false;
        }
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        if (player.isSleeping()) {
            player.wakeup(false);
        }
        if (!canRefresh(player)) {
            return false;
        }
        NickIdentity identity = session.identity;
        boolean showNick = identity != null && !identity.sameName(takenName) && !session.denied
                && settings.policy().shows(shownFor(player), inRound(owner));
        if (showNick) {
            show(player, session, identity,
                    identity.poolSkin() ? skins.cached(identity.skin()) : Optional.empty());
        } else if (session.shown != null) {
            reveal(player, session);
        }
        return !player.getName().equalsIgnoreCase(takenName);
    }

    private void revealDenied(Player player, Session session) {
        if (player.isOnline() && sessions.get(player.getUniqueId()) == session && session.denied
                && session.shown != null && canRefresh(player)) {
            reveal(player, session);
        }
    }

    private boolean show(Player player, Session session, NickIdentity identity,
                         Optional<NickStore.StoredSkin> skin) {
        UUID uuid = player.getUniqueId();
        String previous = player.getName();
        Optional<NickRegistry.Shown> before = registry.shown(uuid);
        NickIdentity shownBefore = session.shown;
        PlayerProfile profile = profileOf(uuid, identity.name(),
                disguisedProperties(identity, skin, session.realProperties));
        registry.put(uuid, new NickRegistry.Shown(identity.name(), session.realName, identity.skin()));
        session.shown = identity;
        if (!swap(player, profile, previous)) {
            before.ifPresentOrElse(shown -> registry.put(uuid, shown), () -> registry.remove(uuid));
            session.shown = shownBefore;
            session.broken = true;
            return false;
        }
        session.broken = false;
        if (inRound(uuid)) {
            session.roundShown = identity;
        }
        disguisePermissions(uuid);
        events.publish(new NickChangedEvent(uuid, session.realName, previous, identity.name(), true));
        return true;
    }

    private void reveal(Player player, Session session) {
        UUID uuid = player.getUniqueId();
        String previous = player.getName();
        PlayerProfile profile = profileOf(uuid, session.realName, session.realProperties);
        Optional<NickRegistry.Shown> before = registry.shown(uuid);
        NickIdentity shownBefore = session.shown;
        registry.remove(uuid);
        session.shown = null;
        if (!swap(player, profile, previous)) {
            before.ifPresent(shown -> registry.put(uuid, shown));
            session.shown = shownBefore;
            session.broken = true;
            return;
        }
        session.broken = false;
        permissions.undisguise(uuid);
        events.publish(new NickChangedEvent(uuid, session.realName, previous, session.realName, false));
    }

    private boolean swap(Player player, PlayerProfile profile, String previousName) {
        boolean plainDisplayName;
        try {
            InventoryType open = player.getOpenInventory().getType();
            if (open != InventoryType.CRAFTING && open != InventoryType.CREATIVE) {
                player.closeInventory();
            }
            plainDisplayName = PlainTextComponentSerializer.plainText().serialize(player.displayName())
                    .equals(previousName);
            player.setPlayerProfile(profile);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not change the profile of " + previousName, e);
            return false;
        }
        try {
            if (nameIndex != null) {
                nameIndex.rename(player, previousName, player.getName());
            }
            if (plainDisplayName) {
                player.displayName(Component.text(player.getName()));
            }
            rehide(player);
            if (modules.enabled(CoreModules.TABLIST)) {
                tablist.handleRename(player, previousName);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not refresh the views after renaming " + previousName, e);
        }
        return true;
    }

    private void rehide(Player player) {
        if (vanish.vanished(player)) {
            for (Player viewer : plugin.getServer().getOnlinePlayers()) {
                if (!viewer.equals(player) && !vanish.canSee(viewer, player)) {
                    viewer.hidePlayer(plugin, player);
                }
            }
        }
        vanish.hideVanishedFrom(player);
    }

    private void restoreLoginName(Player player, String loginName) {
        if (!player.equals(plugin.getServer().getPlayerExact(loginName))) {
            return;
        }
        try {
            for (Player viewer : plugin.getServer().getOnlinePlayers()) {
                if (!viewer.equals(player)) {
                    viewer.hidePlayer(plugin, player);
                }
            }
            player.setPlayerProfile(profileOf(player.getUniqueId(), loginName,
                    List.copyOf(player.getPlayerProfile().getProperties())));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.FINE, "Could not restore the login name of " + loginName, e);
        }
    }

    private void disguisePermissions(UUID uuid) {
        NickSettings.FakeRank rank = settings.fakeRank();
        if (!rank.luckPermsDisguise() || !permissions.luckPerms()) {
            return;
        }
        try {
            FakeRankResolver.Look look = fakeRank.look();
            permissions.disguise(uuid, look.disguisePrefix(), rank.luckPermsPriority(), look.disguiseColor());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not add the LuckPerms disguise", e);
        }
    }

    private Optional<NickIdentity> generate(UUID uuid, long now, String exclude, NickIdentity.Kind kind) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String skin = skins.chooseSkin(random);
        for (String candidate : names.candidates(random, GENERATED_ATTEMPTS)) {
            if (candidate.equalsIgnoreCase(exclude) || !nameFree(candidate, uuid, now)) {
                continue;
            }
            NickIdentity identity = new NickIdentity(candidate, skin, kind);
            if (store.claim(uuid, identity, config.serverId(), now, claimExpiry())) {
                return Optional.of(identity);
            }
        }
        return Optional.empty();
    }

    private boolean nameFree(String name, UUID uuid, long now) {
        return names.allowed(name) && !usedByOtherSession(name, uuid) && conflict(name, uuid, now) == Conflict.FREE;
    }

    private Conflict conflict(String name, UUID uuid, long now) {
        if (!names.allowed(name) || profiles.lookup(name).join().isPresent()
                || store.claimedByOther(name, uuid, now, claimExpiry())) {
            return Conflict.LASTING;
        }
        return usedByOtherSession(name, uuid) ? Conflict.TRANSIENT : Conflict.FREE;
    }

    private boolean usedByOtherSession(String name, UUID uuid) {
        for (Session session : sessions.values()) {
            if (!session.uuid.equals(uuid) && session.uses(name)) {
                return true;
            }
        }
        for (Map.Entry<UUID, LoginState> entry : logins.entrySet()) {
            LoginState state = entry.getValue();
            if (!entry.getKey().equals(uuid) && (state.realName().equalsIgnoreCase(name)
                    || (state.identity() != null && state.identity().sameName(name)))) {
                return true;
            }
        }
        return false;
    }

    private void touchQuietly(UUID uuid, NickIdentity identity, long now) {
        try {
            store.touch(uuid, identity, config.serverId(), now);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not refresh the nick claim " + identity.name(), e);
        }
    }

    private Optional<NickStore.StoredSkin> skinFor(NickIdentity identity) {
        if (!identity.poolSkin()) {
            return Optional.empty();
        }
        try {
            return skins.load(identity.skin());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not load the nick skin " + identity.skin(), e);
            return Optional.empty();
        }
    }

    private PlayerProfile profileOf(UUID uuid, String name, List<ProfileProperty> properties) {
        PlayerProfile profile = plugin.getServer().createProfileExact(uuid, name);
        profile.setProperties(properties);
        return profile;
    }

    static List<ProfileProperty> disguisedProperties(NickIdentity identity, Optional<NickStore.StoredSkin> skin,
                                                     List<ProfileProperty> realProperties) {
        List<ProfileProperty> properties = new ArrayList<>();
        for (ProfileProperty property : realProperties) {
            if (identity.ownSkin() || !SkinPool.TEXTURES.equals(property.getName())) {
                properties.add(property);
            }
        }
        if (!identity.ownSkin()) {
            skin.ifPresent(stored -> properties.add(
                    new ProfileProperty(SkinPool.TEXTURES, stored.value(), stored.signature())));
        }
        return List.copyOf(properties);
    }

    private static boolean mirror(Map<String, String> profileSettings, NickIdentity identity) {
        if (identity == null) {
            boolean changed = profileSettings.remove(SETTING_NAME) != null;
            return profileSettings.remove(SETTING_SKIN) != null || changed;
        }
        boolean changed = !identity.name().equals(profileSettings.put(SETTING_NAME, identity.name()));
        return !identity.skin().equals(profileSettings.put(SETTING_SKIN, identity.skin())) || changed;
    }

    private static Optional<NickIdentity> fromSettings(Map<String, String> profileSettings) {
        String name = profileSettings.get(SETTING_NAME);
        if (name == null || !NickNames.validFormat(name)) {
            return Optional.empty();
        }
        return Optional.of(new NickIdentity(name, profileSettings.get(SETTING_SKIN)));
    }

    private Optional<Map<String, String>> settingsOf(UUID uuid) {
        return profiles.getCached(uuid).map(profile -> profile.getSettings());
    }

    private NickIdentity.Kind autoKind() {
        return settings.policy().when() == NickPolicy.When.IN_ROUND ? NickIdentity.Kind.ROUND : NickIdentity.Kind.AUTO;
    }

    private long claimExpiry() {
        return settings.names().claimExpiryMillis();
    }

    private boolean canRefresh(Player player) {
        return player.isOnline() && !player.isDead() && !player.isSleeping() && !player.isInsideVehicle();
    }

    private void remind() {
        long now = System.currentTimeMillis();
        Map<UUID, NickRegistry.Shown> shown = registry.all();
        reminders.keySet().removeIf(uuid -> !shown.containsKey(uuid));
        for (Map.Entry<UUID, NickRegistry.Shown> entry : shown.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || inRound(entry.getKey()) || vanish.vanished(player)) {
                continue;
            }
            String nick = entry.getValue().nick();
            Reminder reminder = reminders.get(entry.getKey());
            if (reminder == null || !reminder.nick().equals(nick)) {
                reminder = new Reminder(nick, now + settings.actionBarSeconds() * 1000L);
                reminders.put(entry.getKey(), reminder);
            } else if (settings.actionBarSeconds() > 0 && now >= reminder.until() - ACTION_BAR_VISIBLE_MILLIS) {
                continue;
            }
            player.sendActionBar(locale.render("nick.actionbar", PlayerLocales.of(player, profiles),
                    Placeholder.unparsed("nick", nick)));
        }
    }

    private record Reminder(String nick, long until) {
    }

    private void send(Player player, String key, TagResolver... resolvers) {
        player.sendMessage(locale.render(key, PlayerLocales.of(player, profiles), resolvers));
    }

    private void onMain(Runnable task) {
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    private enum Conflict {
        FREE,
        TRANSIENT,
        LASTING
    }

    private static final class Session {

        private final UUID uuid;
        private final String realName;
        private final String loginName;
        private final List<ProfileProperty> realProperties;
        private final AtomicBoolean busy = new AtomicBoolean();
        private volatile NickIdentity identity;
        private volatile NickIdentity shown;
        private volatile NickIdentity roundShown;
        private volatile boolean optedOut;
        private volatile boolean inRound;
        private volatile boolean broken;
        private volatile boolean denied;

        private Session(UUID uuid, String realName, String loginName, List<ProfileProperty> realProperties) {
            this.uuid = uuid;
            this.realName = realName;
            this.loginName = loginName;
            this.realProperties = List.copyOf(realProperties);
        }

        private boolean uses(String name) {
            NickIdentity currentIdentity = identity;
            NickIdentity currentShown = shown;
            return realName.equalsIgnoreCase(name) || loginName.equalsIgnoreCase(name)
                    || (currentIdentity != null && currentIdentity.sameName(name))
                    || (currentShown != null && currentShown.sameName(name));
        }
    }

    private record LoginState(String realName, List<ProfileProperty> realProperties, NickIdentity identity,
                              NickIdentity shown, long created) {
    }

    private record Prepared(NickResult failure, NickIdentity identity, Optional<NickStore.StoredSkin> skin) {

        private static Prepared failed(NickResult failure) {
            return new Prepared(failure, null, Optional.empty());
        }
    }
}
