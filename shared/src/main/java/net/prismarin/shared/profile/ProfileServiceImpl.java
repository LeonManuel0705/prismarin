package net.prismarin.shared.profile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.shared.storage.ProfileRepository;

public final class ProfileServiceImpl implements ProfileService {

    private final ProfileRepository repository;
    private final String defaultLocale;
    private final ExecutorService executor;
    private final ConcurrentHashMap<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> chains = new ConcurrentHashMap<>();

    public ProfileServiceImpl(ProfileRepository repository, String defaultLocale, ExecutorService executor) {
        this.repository = repository;
        this.defaultLocale = defaultLocale;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<PlayerProfile> load(UUID uuid, String name) {
        return ordered(uuid, () -> {
            PlayerProfile profile = repository.find(uuid).orElse(null);
            if (profile == null) {
                profile = PlayerProfile.create(uuid, name, defaultLocale);
                repository.save(profile);
            } else {
                profile.setName(name);
                profile.setLastSeen(Instant.now());
            }
            cache.put(uuid, profile);
            return profile;
        });
    }

    @Override
    public Optional<PlayerProfile> getCached(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    @Override
    public CompletableFuture<Void> save(PlayerProfile profile) {
        return ordered(profile.getUuid(), () -> {
            repository.save(profile);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> unload(UUID uuid) {
        return ordered(uuid, () -> {
            PlayerProfile profile = cache.remove(uuid);
            if (profile != null) {
                profile.setLastSeen(Instant.now());
                repository.save(profile);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<PlayerProfile> modify(UUID uuid, Consumer<PlayerProfile> action) {
        return ordered(uuid, () -> {
            PlayerProfile profile = cache.get(uuid);
            if (profile == null) {
                profile = repository.find(uuid)
                        .orElseThrow(() -> new IllegalStateException("no profile stored for " + uuid));
            }
            action.accept(profile);
            repository.save(profile);
            return profile;
        });
    }

    private <T> CompletableFuture<T> ordered(UUID uuid, Supplier<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        CompletableFuture<Void> tail = chains.compute(uuid, (key, previous) -> {
            CompletableFuture<Void> base = previous == null ? CompletableFuture.completedFuture(null) : previous;
            return base.handleAsync((unused, error) -> {
                try {
                    result.complete(task.get());
                } catch (Throwable throwable) {
                    result.completeExceptionally(throwable);
                }
                return null;
            }, executor);
        });
        tail.whenComplete((unused, error) -> chains.remove(uuid, tail));
        return result;
    }
}
