package net.prismarin.core.cosmetics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.prismarin.api.PrismarinSystems;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticEffectEvent;
import net.prismarin.api.cosmetics.CosmeticEffectKind;
import net.prismarin.api.cosmetics.CosmeticFrame;
import net.prismarin.api.cosmetics.CosmeticHat;
import net.prismarin.api.cosmetics.CosmeticParticles;
import net.prismarin.api.cosmetics.CosmeticPoint;
import net.prismarin.api.cosmetics.CosmeticRarity;
import net.prismarin.api.cosmetics.CosmeticRenderer;
import net.prismarin.api.cosmetics.CosmeticResult;
import net.prismarin.api.cosmetics.CosmeticService;
import net.prismarin.api.cosmetics.CosmeticSlot;
import net.prismarin.api.cosmetics.CosmeticSource;
import net.prismarin.api.cosmetics.CosmeticSpot;
import net.prismarin.api.cosmetics.CosmeticViewFilter;
import net.prismarin.api.cosmetics.PlayerCosmetics;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.reward.RewardHandlers;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.vanish.VanishApi;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.cosmetics.ActiveSlots;
import net.prismarin.shared.cosmetics.CosmeticActions;
import net.prismarin.shared.cosmetics.CosmeticCatalog;
import net.prismarin.shared.cosmetics.CosmeticCategory;
import net.prismarin.shared.cosmetics.CosmeticLedger;
import net.prismarin.shared.cosmetics.CosmeticShapes;
import net.prismarin.shared.cosmetics.HatSafety;
import net.prismarin.shared.cosmetics.HatTextures;
import net.prismarin.shared.cosmetics.ProjectileTrails;
import net.prismarin.shared.cosmetics.ViewRules;
import net.prismarin.shared.platform.CoreServices;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

@Singleton
public final class PaperCosmetics implements CosmeticService, Listener {

    public static final String LOBBY_PLUGIN = "PrismarinLobby";
    private static final double PREVIEW_DISTANCE = 2.6;
    private static final int PREVIEW_PAUSE = 20;
    private static final int PREVIEW_FLIGHT = 30;

    private final PrismarinCorePlugin plugin;
    private final CoreServices services;
    private final ExecutorService executor;
    private final EconomyService economy;
    private final ProfileService profiles;
    private final ServerDirectory directory;
    private final CoreConfig config;
    private final CosmeticTexts texts;
    private final CosmeticCatalog catalog = new CosmeticCatalog();
    private final CosmeticShapes shapes = new CosmeticShapes();
    private final ParticleSpawner spawner = new ParticleSpawner();
    private final List<CosmeticViewFilter> filters = new CopyOnWriteArrayList<>();
    private final Map<UUID, PlayerCosmetics> cache = new ConcurrentHashMap<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private volatile CosmeticSettings settings;
    private volatile ActiveSlots active;
    private volatile CosmeticActions actions;
    private final Map<CosmeticSlot, Boolean> automatic = new ConcurrentHashMap<>();
    private ParticleTicker ticker;
    private HatService hats;
    private PaperEffects effects;
    private ProjectileTrails<Projectile> trails;
    private AutoTriggers triggers;
    private Subscription reward;

    @Inject
    public PaperCosmetics(PrismarinCorePlugin plugin, CoreServices services, ExecutorService executor,
                          EconomyService economy, ProfileService profiles, ServerDirectory directory,
                          CoreConfig config, CosmeticTexts texts) {
        this.plugin = plugin;
        this.services = services;
        this.executor = executor;
        this.economy = economy;
        this.profiles = profiles;
        this.directory = directory;
        this.config = config;
        this.texts = texts;
    }

    public synchronized void start() {
        if (actions != null) {
            return;
        }
        CosmeticSettings loaded = loadSettings();
        settings = loaded;
        catalog.load(loaded.cosmetics(), loaded.categories(), loaded.rarities());
        active = new ActiveSlots(config.serverId(), group(), loaded.activeGroups());
        actions = new CosmeticActions(catalog, new CosmeticLedger(services.documents(), Clock.systemUTC()),
                economy, services.events(), executor, this::onMain, (uuid, cosmetic) -> {
                    Player player = plugin.getServer().getPlayer(uuid);
                    return player != null && permitted(player, cosmetic);
                });
        ticker = new ParticleTicker(plugin, new ParticleTicker.Painter() {
            @Override
            public int paint(Player wearer, Cosmetic cosmetic, long tick, boolean moving, int budget) {
                return paintWorn(wearer, cosmetic, tick, moving, budget);
            }

            @Override
            public int preview(Player viewer, Cosmetic cosmetic, Location anchor, long tick) {
                return paintPreview(viewer, cosmetic, anchor, tick);
            }
        }, loaded.performance().intervalTicks(), loaded.performance().maxParticles());
        CosmeticSettings.Performance limits = loaded.performance();
        effects = new PaperEffects(plugin, spawner, this::points, this::range, limits.maxParticles(),
                limits.maxEffects(), limits.maxEffectEntities());
        CosmeticSettings.Triggers rules = loaded.triggers();
        trails = new ProjectileTrails<>(PaperEffects.clock(plugin), new ProjectileTrails.Driver<>() {
            @Override
            public boolean flying(Projectile projectile) {
                return inFlight(projectile);
            }

            @Override
            public boolean paint(Projectile projectile, UUID owner, Cosmetic cosmetic, long age) {
                return paintTrail(projectile, owner, cosmetic, age);
            }
        }, rules.trailInterval(), rules.trailTicks(), rules.trailTracked());
        triggers = new AutoTriggers(this);
        hats = new HatService(plugin, this::hatItem, this::refresh);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(hats, plugin);
        plugin.getServer().getPluginManager().registerEvents(effects.entities(), plugin);
        plugin.getServer().getPluginManager().registerEvents(triggers, plugin);
        reward = new CosmeticReward(() -> this, this::tellUnlocked).register(RewardHandlers.global());
        validate();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            hats.cleanUp(player);
            load(player.getUniqueId());
        }
    }

    public synchronized void stop() {
        if (actions == null) {
            return;
        }
        HandlerList.unregisterAll(this);
        HandlerList.unregisterAll(hats);
        HandlerList.unregisterAll(triggers);
        hats.takeOffAll();
        ticker.stop();
        ticker = null;
        trails.stop();
        effects.stop();
        HandlerList.unregisterAll(effects.entities());
        if (reward != null) {
            reward.unsubscribe();
            reward = null;
        }
        cache.clear();
        actions = null;
    }

    public void reload() {
        CosmeticSettings fresh = loadSettings();
        settings = fresh;
        catalog.load(fresh.cosmetics(), fresh.categories(), fresh.rarities());
        if (active != null) {
            active.defaults(fresh.activeGroups());
        }
        warned.clear();
        onMain(() -> {
            if (ticker != null) {
                CosmeticSettings.Performance limits = fresh.performance();
                CosmeticSettings.Triggers rules = fresh.triggers();
                ticker.configure(limits.intervalTicks(), limits.maxParticles());
                effects.configure(limits.maxParticles(), limits.maxEffects(), limits.maxEffectEntities());
                trails.configure(rules.trailInterval(), rules.trailTicks(), rules.trailTracked());
                validate();
                refreshAll();
            }
        });
    }

    public CosmeticSettings settings() {
        return settings;
    }

    public CosmeticCatalog catalog() {
        return catalog;
    }

    public boolean economyOn() {
        return economy.enabled() && config.systemEnabled(PrismarinSystems.ECONOMY);
    }

    public boolean forSale(Cosmetic cosmetic) {
        return economyOn() && cosmetic.priceTag().flatMap(price -> economy.currency(price.currency())).isPresent();
    }

    public long balance(Player player, String currency) {
        return profiles.getCached(player.getUniqueId())
                .map(profile -> profile.balance(Currency.of(currency))).orElse(0L);
    }

    public CosmeticRarity rarityOf(Cosmetic cosmetic) {
        return catalog.rarityOf(cosmetic);
    }

    public boolean permitted(Player player, Cosmetic cosmetic) {
        return player.hasPermission(PERMISSION_ALL)
                || !cosmetic.permission().isEmpty() && player.hasPermission(cosmetic.permission());
    }

    public boolean ownsHere(Player player, Cosmetic cosmetic) {
        PlayerCosmetics state = cache.get(player.getUniqueId());
        return state != null && state.owns(cosmetic.id()) || permitted(player, cosmetic);
    }

    public boolean previewable(Cosmetic cosmetic) {
        return settings.previewSeconds() > 0 && cosmetic.particles().isPresent();
    }

    public boolean preview(Player viewer, Cosmetic cosmetic) {
        if (!previewable(cosmetic) || ticker == null) {
            return false;
        }
        Location eye = viewer.getLocation();
        Vector direction = eye.getDirection().setY(0.0);
        if (direction.lengthSquared() < 1.0E-4) {
            direction = new Vector(0.0, 0.0, 1.0);
        }
        Location anchor = eye.clone().add(direction.normalize().multiply(PREVIEW_DISTANCE));
        anchor.setYaw(eye.getYaw() + 180.0f);
        anchor.setPitch(0.0f);
        effects.stopPreviews(viewer.getUniqueId());
        ticker.preview(viewer.getUniqueId(), cosmetic, anchor, settings.previewSeconds());
        return true;
    }

    public CompletableFuture<CosmeticResult> unequipAll(UUID player) {
        return onMainFuture(() -> actions().unequipAll(player)).thenApply(outcome -> applied(player, outcome));
    }

    public Material icon(Cosmetic cosmetic) {
        Material fallback = material(catalog.category(cosmetic.slot()).icon(), Material.CHEST);
        if (cosmetic.hat().isPresent()) {
            return material(cosmetic.icon().isEmpty() ? cosmetic.hat().get().material() : cosmetic.icon(), fallback);
        }
        return cosmetic.icon().isEmpty() ? fallback : material(cosmetic.icon(), fallback);
    }

    public ItemBuilder iconBuilder(Cosmetic cosmetic) {
        ItemBuilder builder = ItemBuilder.of(icon(cosmetic));
        cosmetic.hat().filter(CosmeticHat::head).filter(hat -> cosmetic.icon().isEmpty())
                .flatMap(hat -> HatTextures.hash(hat.texture())).ifPresent(builder::headTexture);
        return builder;
    }

    public static Material material(String name, Material fallback) {
        Material material = name == null || name.isBlank() ? null : Material.matchMaterial(name);
        return material != null && material.isItem() && !material.isAir() ? material : fallback;
    }

    @Override
    public List<Cosmetic> cosmetics() {
        return catalog.all();
    }

    @Override
    public Optional<Cosmetic> cosmetic(String id) {
        return catalog.find(id);
    }

    @Override
    public List<CosmeticSlot> slots() {
        return catalog.categories().stream().map(CosmeticCategory::slot).toList();
    }

    @Override
    public List<CosmeticRarity> rarities() {
        return catalog.rarities();
    }

    @Override
    public Optional<CosmeticRarity> rarity(String id) {
        return catalog.rarity(id);
    }

    @Override
    public CompletableFuture<PlayerCosmetics> load(UUID player) {
        return actions().state(player).thenApply(state -> {
            remember(player, state);
            return state;
        });
    }

    @Override
    public Optional<PlayerCosmetics> cached(UUID player) {
        return player == null ? Optional.empty() : Optional.ofNullable(cache.get(player));
    }

    @Override
    public boolean owns(UUID player, String id) {
        Optional<Cosmetic> cosmetic = catalog.find(id);
        if (player == null || cosmetic.isEmpty()) {
            return false;
        }
        PlayerCosmetics state = cache.get(player);
        if (state != null && state.owns(id)) {
            return true;
        }
        Player online = plugin.getServer().getPlayer(player);
        return online != null && permitted(online, cosmetic.get());
    }

    @Override
    public Optional<Cosmetic> equipped(UUID player, CosmeticSlot slot) {
        PlayerCosmetics state = player == null ? null : cache.get(player);
        if (state == null || !catalog.enabled(slot)) {
            return Optional.empty();
        }
        return state.equipped(slot).flatMap(catalog::find).filter(cosmetic -> owns(player, cosmetic.id()));
    }

    @Override
    public CompletableFuture<CosmeticResult> purchase(UUID player, String id) {
        return onMainFuture(() -> actions().purchase(player, name(player), key(id)))
                .thenApply(outcome -> applied(player, outcome));
    }

    @Override
    public CompletableFuture<CosmeticResult> unlock(UUID player, String id, CosmeticSource source) {
        return actions().unlock(player, name(player), key(id), source).thenApply(outcome -> applied(player, outcome));
    }

    @Override
    public CompletableFuture<CosmeticResult> revoke(UUID player, String id) {
        return actions().revoke(player, key(id)).thenApply(outcome -> applied(player, outcome));
    }

    @Override
    public CompletableFuture<CosmeticResult> equip(UUID player, String id) {
        return onMainFuture(() -> actions().equip(player, key(id))).thenApply(outcome -> applied(player, outcome));
    }

    @Override
    public CompletableFuture<CosmeticResult> unequip(UUID player, CosmeticSlot slot) {
        return actions().unequip(player, slot).thenApply(outcome -> applied(player, outcome));
    }

    @Override
    public CompletableFuture<Boolean> hideOthers(UUID player, boolean hide) {
        return actions().hide(player, hide).thenApply(state -> {
            remember(player, state);
            return state.hidesOthers();
        });
    }

    @Override
    public Subscription register(Cosmetic cosmetic) {
        Subscription registered = catalog.register(cosmetic);
        refreshAllLater();
        return () -> {
            registered.unsubscribe();
            refreshAllLater();
        };
    }

    @Override
    public Subscription registerShape(String shape, CosmeticRenderer renderer) {
        Subscription registered = shapes.register(shape, renderer);
        warned.remove("shape:" + shape);
        return registered;
    }

    @Override
    public Subscription registerEffectKind(String kind, CosmeticEffectKind effect) {
        PaperEffects current = effects;
        if (current == null) {
            throw new IllegalStateException("cosmetics are not started");
        }
        return current.register(kind, effect);
    }

    @Override
    public Subscription addViewFilter(CosmeticViewFilter filter) {
        Objects.requireNonNull(filter, "filter");
        filters.add(filter);
        return () -> filters.remove(filter);
    }

    @Override
    public boolean active(CosmeticSlot slot) {
        ActiveSlots current = active;
        return current != null && slot != null && current.active(catalog.category(slot));
    }

    @Override
    public void enable(CosmeticSlot slot) {
        activeSlots().set(slot, true);
        refreshAllLater();
    }

    @Override
    public void disable(CosmeticSlot slot) {
        activeSlots().set(slot, false);
        refreshAllLater();
    }

    @Override
    public void reset(CosmeticSlot slot) {
        activeSlots().reset(slot);
        refreshAllLater();
    }

    @Override
    public boolean playEffect(UUID player, CosmeticSlot slot, CosmeticSpot spot) {
        if (spot == null) {
            return false;
        }
        return play(player, slot, spot, CosmeticEffectEvent.Trigger.API);
    }

    @Override
    public boolean playEffect(UUID player, CosmeticSlot slot) {
        return play(player, slot, null, CosmeticEffectEvent.Trigger.API);
    }

    @Override
    public boolean playEffect(UUID player, Cosmetic cosmetic, CosmeticSpot spot) {
        Player online = player == null ? null : plugin.getServer().getPlayer(player);
        if (online == null || cosmetic == null || cosmetic.particles().isEmpty()
                || !catalog.enabled(cosmetic.slot())) {
            return false;
        }
        return schedule(online, cosmetic, spot, CosmeticEffectEvent.Trigger.API);
    }

    @Override
    public int stopEffects(UUID player) {
        PaperEffects current = effects;
        if (player == null || current == null) {
            return 0;
        }
        if (plugin.getServer().isPrimaryThread()) {
            return current.stop(player);
        }
        onMain(() -> current.stop(player));
        return 0;
    }

    @Override
    public boolean trail(UUID player, UUID projectile) {
        if (player == null || projectile == null || trails == null) {
            return false;
        }
        if (!plugin.getServer().isPrimaryThread()) {
            onMain(() -> trail(player, projectile));
            return true;
        }
        Player online = plugin.getServer().getPlayer(player);
        Entity entity = plugin.getServer().getEntity(projectile);
        return online != null && entity instanceof Projectile flying && flying.isValid()
                && startTrail(online, flying, CosmeticEffectEvent.Trigger.API);
    }

    @Override
    public boolean effectEntity(UUID entity) {
        PaperEffects current = effects;
        return current != null && current.entities().marked(entity);
    }

    @Override
    public boolean automatic(CosmeticSlot slot) {
        if (slot == null) {
            return false;
        }
        Boolean override = automatic.get(slot);
        return override != null ? override : settings.triggers().auto(slot);
    }

    @Override
    public void setAutomatic(CosmeticSlot slot, boolean on) {
        automatic.put(triggered(slot), on);
    }

    @Override
    public void resetAutomatic(CosmeticSlot slot) {
        automatic.remove(triggered(slot));
    }

    boolean play(UUID player, CosmeticSlot slot, CosmeticSpot spot, CosmeticEffectEvent.Trigger trigger) {
        Player online = player == null ? null : plugin.getServer().getPlayer(player);
        if (online == null || slot == null) {
            return false;
        }
        Optional<Cosmetic> cosmetic = shown(online, slot).filter(found -> found.particles().isPresent());
        return cosmetic.isPresent() && schedule(online, cosmetic.get(), spot, trigger);
    }

    boolean playAt(Player wearer, CosmeticSlot slot, Location at, CosmeticEffectEvent.Trigger trigger) {
        Optional<Cosmetic> cosmetic = shown(wearer, slot).filter(found -> found.particles().isPresent());
        return cosmetic.isPresent() && startEffect(wearer, cosmetic.get(), at, false, trigger);
    }

    boolean startTrail(Player shooter, Projectile projectile, CosmeticEffectEvent.Trigger trigger) {
        Optional<Cosmetic> cosmetic = shown(shooter, CosmeticSlot.PROJECTILE_TRAIL)
                .filter(found -> found.particles().isPresent());
        if (cosmetic.isEmpty() || trails == null || !wearerShows(shooter, true)
                || effects.entities().marked(projectile)) {
            return false;
        }
        Location at = projectile.getLocation();
        if (!published(shooter, cosmetic.get(), at, trigger)) {
            return false;
        }
        return trails.track(projectile.getUniqueId(), projectile, shooter.getUniqueId(), cosmetic.get());
    }

    void landed(UUID projectile) {
        if (trails != null) {
            trails.land(projectile);
        }
    }

    void unloaded(List<Entity> entities) {
        if (trails == null || entities.isEmpty()) {
            return;
        }
        Set<UUID> gone = new HashSet<>();
        entities.forEach(entity -> gone.add(entity.getUniqueId()));
        trails.forget(projectile -> gone.contains(projectile.getUniqueId()));
    }

    Optional<Cosmetic> playCommand(Player target, CosmeticSlot slot, Location at) {
        Optional<Cosmetic> cosmetic = shown(target, slot).filter(found -> found.particles().isPresent());
        if (cosmetic.isEmpty()) {
            return Optional.empty();
        }
        boolean played = startEffect(target, cosmetic.get(), at == null ? target.getLocation() : at, at == null,
                CosmeticEffectEvent.Trigger.COMMAND);
        return played ? cosmetic : Optional.empty();
    }

    boolean visibleWearer(Player player) {
        return wearerShows(player, true);
    }

    private boolean schedule(Player wearer, Cosmetic cosmetic, CosmeticSpot spot,
                             CosmeticEffectEvent.Trigger trigger) {
        World world = spot == null ? null : plugin.getServer().getWorld(spot.world());
        if (spot != null && world == null) {
            return false;
        }
        if (plugin.getServer().isPrimaryThread()) {
            return startEffect(wearer, cosmetic, location(wearer, world, spot), spot == null, trigger);
        }
        onMain(() -> {
            if (wearer.isOnline()) {
                startEffect(wearer, cosmetic, location(wearer, world, spot), spot == null, trigger);
            }
        });
        return true;
    }

    private static Location location(Player wearer, World world, CosmeticSpot spot) {
        return spot == null ? wearer.getLocation()
                : new Location(world, spot.x(), spot.y(), spot.z(), (float) spot.yaw(), 0.0f);
    }

    private boolean startEffect(Player wearer, Cosmetic cosmetic, Location at, boolean follow,
                                CosmeticEffectEvent.Trigger trigger) {
        PaperEffects current = effects;
        if (current == null || !wearer.isOnline() || at.getWorld() == null || !wearerShows(wearer, true)) {
            return false;
        }
        if (!published(wearer, cosmetic, at, trigger)) {
            return false;
        }
        List<Player> viewers = viewers(wearer, at, cosmetic.slot());
        if (viewers.isEmpty()) {
            return true;
        }
        return current.play(wearer.getUniqueId(), cosmetic, at, follow, false, viewers);
    }

    private boolean published(Player wearer, Cosmetic cosmetic, Location at, CosmeticEffectEvent.Trigger trigger) {
        CosmeticEffectEvent event = new CosmeticEffectEvent(wearer.getUniqueId(), cosmetic,
                CosmeticSpot.of(at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), at.getYaw()), trigger);
        services.events().publish(event);
        return !event.isCancelled();
    }

    private static CosmeticSlot triggered(CosmeticSlot slot) {
        if (!CosmeticSlot.PROJECTILE_TRAIL.equals(slot) && !CosmeticSlot.KILL_EFFECT.equals(slot)) {
            throw new IllegalArgumentException("the slot " + (slot == null ? "null" : slot.id())
                    + " has no automatic trigger, only projectile-trail and kill-effect have one");
        }
        return slot;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        hats.cleanUp(player);
        load(player.getUniqueId()).exceptionally(error -> {
            plugin.getLogger().log(Level.WARNING, "Could not load the cosmetics of " + player.getName(), error);
            return null;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        ticker.forget(id);
        trails.forgetOwner(id);
        effects.stop(id);
        effects.stopPreviews(id);
        cache.remove(id);
    }

    void refresh(Player player) {
        if (ticker == null || !player.isOnline()) {
            return;
        }
        Cosmetic trail = shown(player, CosmeticSlot.TRAIL).filter(found -> found.particles().isPresent())
                .orElse(null);
        Cosmetic aura = shown(player, CosmeticSlot.AURA).filter(found -> found.particles().isPresent()).orElse(null);
        ticker.wear(player.getUniqueId(), trail, aura);
        hats.wear(player, shown(player, CosmeticSlot.HAT).filter(found -> found.hat().isPresent()).orElse(null));
    }

    void tellUnlocked(UUID uuid, CosmeticResult result) {
        Player player = plugin.getServer().getPlayer(uuid);
        if (result.status() == CosmeticResult.Status.UNKNOWN) {
            plugin.getLogger().warning("A reward names a cosmetic that is not in " + CosmeticSettings.FILE_NAME);
            return;
        }
        if (player == null || result.cosmetic().isEmpty()) {
            return;
        }
        Locale language = texts.language(player);
        Component name = texts.name(result.cosmetic().get(), rarityOf(result.cosmetic().get()), language, uuid);
        if (result.success()) {
            player.sendMessage(texts.render(language, "cosmetics.unlocked", texts.cosmetic(name)));
        } else if (result.status() == CosmeticResult.Status.ALREADY_OWNED) {
            player.sendMessage(texts.render(language, "cosmetics.unlocked-owned", texts.cosmetic(name)));
        }
    }

    private Optional<Cosmetic> shown(Player player, CosmeticSlot slot) {
        return active(slot) ? equipped(player.getUniqueId(), slot) : Optional.empty();
    }

    private ItemStack hatItem(Player player, Cosmetic cosmetic) {
        Optional<CosmeticHat> look = cosmetic.hat();
        if (look.isEmpty()) {
            return null;
        }
        HatSafety.Verdict verdict = HatSafety.check(look.get().material());
        if (!verdict.usable()) {
            if (warned.add("hat:" + cosmetic.id())) {
                plugin.getLogger().warning("The hat " + cosmetic.id() + " is not worn: " + verdict.reason());
            }
            return null;
        }
        Locale language = texts.language(player);
        ItemBuilder builder = ItemBuilder.of(material(verdict.material(), Material.PLAYER_HEAD))
                .name(texts.name(cosmetic, rarityOf(cosmetic), language, player.getUniqueId()))
                .lore(texts.render(language, "cosmetics.hat-lore"));
        if (look.get().head()) {
            HatTextures.hash(look.get().texture()).ifPresent(builder::headTexture);
        }
        ItemStack item = builder.build();
        item.editMeta(meta -> HatSafety.apply(verdict, hatEditor(meta)));
        return item;
    }

    private HatSafety.Editor hatEditor(ItemMeta meta) {
        return new HatSafety.Editor() {
            @Override
            public void neutralizeAttributes() {
                meta.setAttributeModifiers(null);
                meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(new NamespacedKey(plugin,
                        "cosmetic-hat-armor"), 0.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }

            @Override
            public void removeEnchantments() {
                meta.removeEnchantments();
            }

            @Override
            public void disguise(String model, String asset) {
                meta.setItemModel(NamespacedKey.minecraft(model));
                EquippableComponent equippable = meta.getEquippable();
                equippable.setSlot(EquipmentSlot.HEAD);
                equippable.setModel(NamespacedKey.minecraft(asset));
                meta.setEquippable(equippable);
            }
        };
    }

    private int paintWorn(Player wearer, Cosmetic cosmetic, long tick, boolean moving, int budget) {
        Optional<CosmeticParticles> look = cosmetic.particles();
        if (look.isEmpty() || !wearerShows(wearer, false)) {
            return 0;
        }
        Location at = wearer.getLocation();
        List<Player> viewers = viewers(wearer, at, cosmetic.slot());
        if (viewers.isEmpty()) {
            return 0;
        }
        List<CosmeticPoint> points = points(new CosmeticFrame(wearer.getUniqueId(), tick, at.getYaw(), moving,
                look.get()));
        int cost = points.size() * viewers.size();
        if (cost > budget) {
            return ParticleTicker.DEFERRED;
        }
        spawner.spawn(at.getWorld(), at.getX(), at.getY(), at.getZ(), look.get(), points, viewers, range());
        return Math.max(1, cost);
    }

    private int paintPreview(Player viewer, Cosmetic cosmetic, Location anchor, long tick) {
        Optional<CosmeticParticles> look = cosmetic.particles();
        if (look.isEmpty()) {
            return 0;
        }
        List<Player> only = List.of(viewer);
        CosmeticSlot slot = cosmetic.slot();
        if (slot.effect() && !slot.equals(CosmeticSlot.PROJECTILE_TRAIL)) {
            effects.stopPreviews(viewer.getUniqueId());
            effects.play(viewer.getUniqueId(), cosmetic, anchor, false, true, only);
            return Math.max(PREVIEW_PAUSE, effects.duration(look.get()) + PREVIEW_PAUSE);
        }
        Location at = anchor;
        if (slot.equals(CosmeticSlot.PROJECTILE_TRAIL)) {
            double share = (double) Math.floorMod(tick, PREVIEW_FLIGHT) / PREVIEW_FLIGHT;
            double radians = Math.toRadians(anchor.getYaw());
            double side = (share - 0.5) * 4.0;
            at = anchor.clone().add(-Math.cos(radians) * side, 1.0 + Math.sin(share * Math.PI) * 1.2,
                    -Math.sin(radians) * side);
        }
        List<CosmeticPoint> points = points(new CosmeticFrame(viewer.getUniqueId(), tick, anchor.getYaw(),
                slot.equals(CosmeticSlot.TRAIL) || slot.equals(CosmeticSlot.PROJECTILE_TRAIL), look.get()));
        spawner.spawn(at.getWorld(), at.getX(), at.getY(), at.getZ(), look.get(), points, only, range());
        return 0;
    }

    private boolean inFlight(Projectile projectile) {
        if (!projectile.isValid()) {
            return false;
        }
        if (projectile instanceof AbstractArrow arrow && arrow.isInBlock()) {
            return false;
        }
        return !(projectile instanceof FishHook hook)
                || hook.getState() == FishHook.HookState.UNHOOKED && !hook.isOnGround();
    }

    private boolean paintTrail(Projectile projectile, UUID owner, Cosmetic cosmetic, long age) {
        Player wearer = plugin.getServer().getPlayer(owner);
        Optional<CosmeticParticles> look = cosmetic.particles();
        if (wearer == null || look.isEmpty() || !wearerShows(wearer, true)) {
            return true;
        }
        Location at = projectile.getLocation();
        List<Player> viewers = viewers(wearer, at, CosmeticSlot.PROJECTILE_TRAIL);
        if (viewers.isEmpty()) {
            return true;
        }
        List<CosmeticPoint> points = points(new CosmeticFrame(owner, age, at.getYaw(), true, look.get()));
        if (!effects.budget().take(points.size() * viewers.size())) {
            return false;
        }
        spawner.spawn(at.getWorld(), at.getX(), at.getY(), at.getZ(), look.get(), points, viewers, range());
        return true;
    }

    List<CosmeticPoint> points(CosmeticFrame frame) {
        String shape = frame.look().shape();
        if (!shapes.known(shape) && warned.add("shape:" + shape)) {
            plugin.getLogger().warning("The cosmetic shape " + shape + " is unknown, drawing a point instead. "
                    + "Built in: " + String.join(", ", CosmeticShapes.BUILT_IN));
        }
        try {
            return shapes.render(frame);
        } catch (RuntimeException | LinkageError e) {
            if (warned.add("failed:" + shape)) {
                plugin.getLogger().log(Level.WARNING, "The cosmetic shape " + shape + " of another plugin failed", e);
            }
            return List.of();
        }
    }

    private List<Player> viewers(Player wearer, Location at, CosmeticSlot slot) {
        double range = range();
        List<Player> viewers = new ArrayList<>();
        for (Player viewer : at.getWorld().getPlayers()) {
            boolean self = viewer.getUniqueId().equals(wearer.getUniqueId());
            boolean hides = !self && cached(viewer.getUniqueId()).map(PlayerCosmetics::hidesOthers).orElse(false);
            if (ViewRules.viewerSees(self, self || viewer.canSee(wearer), hides,
                    viewer.getLocation().distanceSquared(at), range, self || filtersAllow(viewer, wearer, slot))) {
                viewers.add(viewer);
            }
        }
        return viewers;
    }

    private boolean filtersAllow(Player viewer, Player wearer, CosmeticSlot slot) {
        for (CosmeticViewFilter filter : filters) {
            try {
                if (!filter.shows(viewer.getUniqueId(), wearer.getUniqueId(), slot)) {
                    return false;
                }
            } catch (RuntimeException | LinkageError e) {
                if (warned.add("filter:" + filter.getClass().getName())) {
                    plugin.getLogger().log(Level.WARNING, "A cosmetic view filter of another plugin failed", e);
                }
            }
        }
        return true;
    }

    private boolean wearerShows(Player player, boolean effect) {
        boolean vanished = VanishApi.service().map(service -> service.vanished(player.getUniqueId())).orElse(false);
        boolean invisible = !effect && (player.isInvisible() || player.hasPotionEffect(PotionEffectType.INVISIBILITY));
        return ViewRules.wearerShows(vanished, player.getGameMode() == GameMode.SPECTATOR, invisible,
                !effect && player.isDead());
    }

    double range() {
        return settings.performance().viewDistance();
    }

    private CosmeticResult applied(UUID player, CosmeticActions.Outcome outcome) {
        outcome.changedState().ifPresent(state -> remember(player, state));
        return outcome.result();
    }

    private void remember(UUID player, PlayerCosmetics state) {
        if (plugin.getServer().getPlayer(player) == null) {
            return;
        }
        cache.put(player, state);
        onMain(() -> {
            Player online = plugin.getServer().getPlayer(player);
            if (online != null) {
                refresh(online);
            }
        });
    }

    private void refreshAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            refresh(player);
        }
    }

    private void refreshAllLater() {
        if (plugin.isEnabled() && ticker != null) {
            onMain(this::refreshAll);
        }
    }

    private void validate() {
        List<String> problems = new ArrayList<>();
        for (Cosmetic cosmetic : catalog.all()) {
            cosmetic.particles().flatMap(spawner::problem)
                    .ifPresent(problem -> problems.add("cosmetics." + cosmetic.id() + ": " + problem));
            cosmetic.hat().filter(hat -> material(hat.material(), null) == null).ifPresent(hat ->
                    problems.add("cosmetics." + cosmetic.id() + ".item '" + hat.material() + "' is no item"));
            if (!cosmetic.icon().isEmpty() && material(cosmetic.icon(), null) == null) {
                problems.add("cosmetics." + cosmetic.id() + ".icon '" + cosmetic.icon() + "' is no item");
            }
        }
        for (CosmeticCategory category : catalog.categories()) {
            if (material(category.icon(), null) == null) {
                problems.add("categories." + category.slot().id() + ".icon '" + category.icon() + "' is no item");
            }
            if (material(category.pane(), null) == null) {
                problems.add("categories." + category.slot().id() + ".pane '" + category.pane() + "' is no item");
            }
        }
        problems.forEach(problem -> plugin.getLogger().warning(CosmeticSettings.FILE_NAME + ": " + problem));
    }

    private CosmeticSettings loadSettings() {
        Path file = plugin.getDataFolder().toPath().resolve(CosmeticSettings.FILE_NAME);
        CosmeticSettings loaded = CosmeticSettings.load(file, config.currencies().stream().map(Currency::id).toList(),
                config.systemEnabled(PrismarinSystems.ECONOMY));
        loaded.problems().forEach(problem -> plugin.getLogger().warning(CosmeticSettings.FILE_NAME + ": " + problem));
        return loaded;
    }

    private String group() {
        return directory.byName(config.serverId()).map(NetworkServer::group).orElseGet(() ->
                plugin.getServer().getPluginManager().getPlugin(LOBBY_PLUGIN) != null
                        ? NetworkServer.GROUP_LOBBY : NetworkServer.GROUP_DEFAULT);
    }

    private String name(UUID player) {
        return profiles.getCached(player).map(PlayerProfile::getName).orElseGet(() -> Optional
                .ofNullable(plugin.getServer().getPlayer(player)).map(Player::getName).orElse(""));
    }

    private ActiveSlots activeSlots() {
        ActiveSlots current = active;
        if (current == null) {
            throw new IllegalStateException("cosmetics are not started");
        }
        return current;
    }

    private CosmeticActions actions() {
        CosmeticActions current = actions;
        if (current == null) {
            throw new IllegalStateException("cosmetics are not started");
        }
        return current;
    }

    private static String key(String id) {
        return id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
    }

    private void onMain(Runnable task) {
        if (plugin.getServer().isPrimaryThread()) {
            task.run();
            return;
        }
        try {
            plugin.getServer().getScheduler().runTask(plugin, task);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.FINE, "Skipped a cosmetics task while the plugin is stopping", e);
        }
    }

    private <T> CompletableFuture<T> onMainFuture(Supplier<CompletableFuture<T>> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable run = () -> {
            try {
                task.get().whenComplete((value, error) -> {
                    if (error != null) {
                        result.completeExceptionally(error);
                    } else {
                        result.complete(value);
                    }
                });
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
}
