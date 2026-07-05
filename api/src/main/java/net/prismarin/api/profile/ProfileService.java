package net.prismarin.api.profile;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Loads, caches and persists {@link PlayerProfile} instances.
 * All I/O happens asynchronously off the server main thread; returned
 * futures complete on the service's own executor.
 */
public interface ProfileService {

    /**
     * Loads the profile for the given player, creating and persisting a
     * fresh one if none exists yet, and places it in the cache.
     *
     * @param uuid the player's unique id
     * @param name the player's current name, used to refresh the stored name
     * @return a future completing with the cached profile
     */
    CompletableFuture<PlayerProfile> load(UUID uuid, String name);

    /**
     * Returns the cached profile without touching storage.
     *
     * @param uuid the player's unique id
     * @return the cached profile, or empty if the player is not loaded
     */
    Optional<PlayerProfile> getCached(UUID uuid);

    /**
     * Persists the given profile to storage.
     *
     * @param profile the profile to save
     * @return a future completing once the write finished
     */
    CompletableFuture<Void> save(PlayerProfile profile);

    /**
     * Removes the profile from the cache and persists its final state.
     *
     * @param uuid the player's unique id
     * @return a future completing once the profile is saved and evicted
     */
    CompletableFuture<Void> unload(UUID uuid);

    /**
     * Applies a mutation to the player's profile and persists the result.
     * Uses the cached instance when present and falls back to loading from
     * storage for offline players.
     *
     * @param uuid the player's unique id
     * @param action the mutation to apply
     * @return a future completing with the modified profile, failing if no
     *         profile exists for the player
     */
    CompletableFuture<PlayerProfile> modify(UUID uuid, Consumer<PlayerProfile> action);
}
