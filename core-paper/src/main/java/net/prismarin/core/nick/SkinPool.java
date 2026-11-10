package net.prismarin.core.nick;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.random.RandomGenerator;
import net.prismarin.core.PrismarinCorePlugin;
import org.bukkit.Bukkit;

@Singleton
public final class SkinPool {

    static final String TEXTURES = "textures";

    private static final long FETCH_PAUSE_MILLIS = 300L;

    private final PrismarinCorePlugin plugin;
    private final NickSettings settings;
    private final NickStore store;
    private final ExecutorService executor;
    private final Map<String, NickStore.StoredSkin> skins = new ConcurrentHashMap<>();
    private volatile boolean running;

    @Inject
    public SkinPool(PrismarinCorePlugin plugin, NickSettings settings, NickStore store, ExecutorService executor) {
        this.plugin = plugin;
        this.settings = settings;
        this.store = store;
        this.executor = executor;
    }

    public void start() {
        running = true;
        if (settings.skins().mode() == NickSettings.SkinMode.POOL && !settings.skins().pool().isEmpty()) {
            executor.execute(this::loadAll);
        }
    }

    public void stop() {
        running = false;
    }

    public String chooseSkin(RandomGenerator random) {
        return switch (settings.skins().mode()) {
            case OWN -> NickIdentity.OWN_SKIN;
            case DEFAULT -> NickIdentity.DEFAULT_SKIN;
            case POOL -> {
                List<String> keys = List.copyOf(skins.keySet());
                if (keys.isEmpty()) {
                    keys = settings.skins().pool().stream().map(name -> name.toLowerCase(Locale.ROOT)).toList();
                }
                yield keys.isEmpty() ? NickIdentity.DEFAULT_SKIN : keys.get(random.nextInt(keys.size()));
            }
        };
    }

    public Optional<NickStore.StoredSkin> cached(String key) {
        return Optional.ofNullable(skins.get(key.toLowerCase(Locale.ROOT)));
    }

    public Optional<NickStore.StoredSkin> load(String key) {
        Optional<NickStore.StoredSkin> cached = cached(key);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<NickStore.StoredSkin> stored = store.skin(key);
        stored.ifPresent(skin -> skins.put(key.toLowerCase(Locale.ROOT), skin));
        return stored;
    }

    public int size() {
        return skins.size();
    }

    private void loadAll() {
        long now = System.currentTimeMillis();
        long maxAge = settings.skins().refreshMillis();
        for (String name : settings.skins().pool()) {
            if (!running) {
                return;
            }
            String key = name.toLowerCase(Locale.ROOT);
            try {
                Optional<NickStore.StoredSkin> stored = store.skin(key);
                stored.ifPresent(skin -> skins.put(key, skin));
                if (stored.isPresent() && stored.get().fresh(now, maxAge)) {
                    continue;
                }
                Optional<NickStore.StoredSkin> fetched = fetch(name, now);
                if (fetched.isPresent()) {
                    store.saveSkin(fetched.get());
                    skins.put(key, fetched.get());
                } else if (stored.isEmpty()) {
                    plugin.getLogger().warning("Nick skin pool: no skin found for '" + name + "'");
                }
                Thread.sleep(FETCH_PAUSE_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Nick skin pool: could not load '" + name + "'", e);
            }
        }
        plugin.getLogger().info("Nick skin pool ready with " + skins.size() + " of "
                + settings.skins().pool().size() + " skins");
    }

    private static Optional<NickStore.StoredSkin> fetch(String name, long now) {
        PlayerProfile profile = Bukkit.createProfile(name);
        if (!profile.complete(true)) {
            return Optional.empty();
        }
        String resolvedName = profile.getName() == null ? name : profile.getName();
        return profile.getProperties().stream()
                .filter(property -> TEXTURES.equals(property.getName()) && property.isSigned())
                .findFirst()
                .map(property -> skin(resolvedName, property, now));
    }

    private static NickStore.StoredSkin skin(String name, ProfileProperty property, long now) {
        return new NickStore.StoredSkin(name, property.getValue(), property.getSignature(), now);
    }
}
