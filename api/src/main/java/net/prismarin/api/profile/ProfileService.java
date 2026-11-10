package net.prismarin.api.profile;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface ProfileService {

    CompletableFuture<PlayerProfile> load(UUID uuid, String name);

    Optional<PlayerProfile> getCached(UUID uuid);

    CompletableFuture<Optional<PlayerProfile>> find(UUID uuid);

    CompletableFuture<Optional<UUID>> lookup(String name);

    CompletableFuture<Void> save(PlayerProfile profile);

    CompletableFuture<Void> unload(UUID uuid);

    CompletableFuture<PlayerProfile> modify(UUID uuid, Consumer<PlayerProfile> action);
}
