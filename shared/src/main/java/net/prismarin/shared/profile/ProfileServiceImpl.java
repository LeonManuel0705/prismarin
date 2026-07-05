package net.prismarin.shared.profile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.shared.storage.ProfileRepository;

public final class ProfileServiceImpl implements ProfileService {

    private final ProfileRepository repository;
    private final String defaultLocale;
    private final ExecutorService executor;
    private final ConcurrentHashMap<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    public ProfileServiceImpl(ProfileRepository repository, String defaultLocale) {
        this(repository, defaultLocale, Executors.newVirtualThreadPerTaskExecutor());
    }

    public ProfileServiceImpl(ProfileRepository repository, String defaultLocale, ExecutorService executor) {
        this.repository = repository;
        this.defaultLocale = defaultLocale;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<PlayerProfile> load(UUID uuid, String name) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerProfile profile = repository.find(uuid).orElse(null);
            if (profile == null) {
                profile = PlayerProfile.create(uuid, name, defaultLocale);
                repository.upsert(profile);
            } else {
                profile.setName(name);
                profile.setLastSeen(Instant.now());
            }
            cache.put(uuid, profile);
            return profile;
        }, executor);
    }

    @Override
    public Optional<PlayerProfile> getCached(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    @Override
    public CompletableFuture<Void> save(PlayerProfile profile) {
        return CompletableFuture.runAsync(() -> repository.upsert(profile), executor);
    }

    @Override
    public CompletableFuture<Void> unload(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            PlayerProfile profile = cache.remove(uuid);
            if (profile != null) {
                profile.setLastSeen(Instant.now());
                repository.upsert(profile);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<PlayerProfile> modify(UUID uuid, Consumer<PlayerProfile> action) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerProfile profile = cache.get(uuid);
            if (profile == null) {
                profile = repository.find(uuid)
                        .orElseThrow(() -> new IllegalStateException("no profile stored for " + uuid));
            }
            action.accept(profile);
            repository.upsert(profile);
            return profile;
        }, executor);
    }
}
