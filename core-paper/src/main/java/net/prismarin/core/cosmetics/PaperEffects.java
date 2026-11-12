package net.prismarin.core.cosmetics;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.logging.Level;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticEffectKind;
import net.prismarin.api.cosmetics.CosmeticFrame;
import net.prismarin.api.cosmetics.CosmeticParticles;
import net.prismarin.api.cosmetics.CosmeticPoint;
import net.prismarin.api.event.Subscription;
import net.prismarin.shared.cosmetics.EffectScheduler;
import net.prismarin.shared.cosmetics.ParticleBudget;
import net.prismarin.shared.cosmetics.TickClock;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

final class PaperEffects {

    private final Plugin plugin;
    private final ParticleSpawner spawner;
    private final Function<CosmeticFrame, List<CosmeticPoint>> shapes;
    private final DoubleSupplier range;
    private final ParticleBudget budget;
    private final EffectEntities entities;
    private final EffectScheduler scheduler;
    private final Map<String, CosmeticEffectKind> builtIn;
    private final Map<String, CosmeticEffectKind> registered = new ConcurrentHashMap<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private long nextId = 1L;

    PaperEffects(Plugin plugin, ParticleSpawner spawner, Function<CosmeticFrame, List<CosmeticPoint>> shapes,
                 DoubleSupplier range, int maxParticles, int maxEffects, int maxEntities) {
        this.plugin = plugin;
        this.spawner = spawner;
        this.shapes = shapes;
        this.range = range;
        this.budget = new ParticleBudget(maxParticles, () -> plugin.getServer().getCurrentTick());
        this.entities = new EffectEntities(plugin, Math.max(1, maxEntities));
        this.scheduler = new EffectScheduler(clock(plugin), maxEffects);
        this.builtIn = BuiltInEffects.kinds(spawner);
    }

    static TickClock clock(Plugin plugin) {
        return task -> {
            if (!plugin.isEnabled()) {
                return () -> {
                };
            }
            BukkitTask started = plugin.getServer().getScheduler().runTaskTimer(plugin, task, 1L, 1L);
            return started::cancel;
        };
    }

    void configure(int maxParticles, int maxEffects, int maxEntities) {
        budget.max(maxParticles);
        scheduler.maxRunning(maxEffects);
        entities.max(Math.max(1, maxEntities));
    }

    boolean play(UUID player, Cosmetic cosmetic, Location at, boolean follow, boolean preview, List<Player> viewers) {
        CosmeticParticles look = cosmetic.particles().orElse(null);
        if (look == null || at.getWorld() == null || viewers.isEmpty()) {
            return false;
        }
        String kindId = look.kind();
        CosmeticEffectKind kind = kind(kindId);
        if (kind == null) {
            if (warned.add("kind:" + kindId)) {
                plugin.getLogger().warning("The cosmetic effect kind " + kindId + " is unknown, drawing particles "
                        + "instead. Built in: " + String.join(", ", BuiltInEffects.NAMES));
            }
            kindId = BuiltInEffects.PARTICLES;
            kind = builtIn.get(kindId);
        }
        int duration = registered.containsKey(kindId) ? look.duration()
                : BuiltInEffects.duration(kindId, look.duration());
        long id = nextId++;
        return scheduler.play(new EffectRun(this, id, player, cosmetic, look, kindId, kind, at, follow, preview,
                viewers, duration));
    }

    int stop(UUID player) {
        return scheduler.cancel(run -> run instanceof EffectRun effect && effect.owner(player) && !effect.preview());
    }

    int stopPreviews(UUID viewer) {
        return scheduler.cancel(run -> run instanceof EffectRun effect && effect.previewing(viewer));
    }

    void stop() {
        scheduler.stop();
        entities.clear();
    }

    int duration(CosmeticParticles look) {
        return registered.containsKey(look.kind()) ? look.duration()
                : BuiltInEffects.duration(look.kind(), look.duration());
    }

    int running() {
        return scheduler.running();
    }

    boolean known(String kind) {
        return kind(kind) != null;
    }

    Subscription register(String id, CosmeticEffectKind kind) {
        String key = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
        if (!CosmeticParticles.KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("'" + id + "' is no effect kind name");
        }
        if (builtIn.containsKey(key)) {
            throw new IllegalArgumentException("the effect kind " + key + " is built in");
        }
        Objects.requireNonNull(kind, "kind");
        if (registered.putIfAbsent(key, kind) != null) {
            throw new IllegalArgumentException("the effect kind " + key + " is already registered");
        }
        warned.remove("kind:" + key);
        warned.remove("failed:" + key);
        return () -> {
            if (registered.remove(key, kind)) {
                scheduler.cancel(run -> run instanceof EffectRun effect && effect.kind() == kind);
            }
        };
    }

    void failed(String kind, Throwable error) {
        if (warned.add("failed:" + kind)) {
            plugin.getLogger().log(Level.WARNING, "The cosmetic effect kind " + kind + " failed and was stopped",
                    error);
        }
    }

    Server server() {
        return plugin.getServer();
    }

    ParticleSpawner spawner() {
        return spawner;
    }

    ParticleBudget budget() {
        return budget;
    }

    EffectEntities entities() {
        return entities;
    }

    double range() {
        return range.getAsDouble();
    }

    List<CosmeticPoint> points(CosmeticFrame frame) {
        return shapes.apply(frame);
    }

    private CosmeticEffectKind kind(String id) {
        CosmeticEffectKind found = builtIn.get(id);
        return found != null ? found : registered.get(id);
    }
}
