package net.prismarin.shared.level;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.prismarin.api.level.LevelChange;
import net.prismarin.api.level.LevelProgress;
import net.prismarin.api.level.LevelService;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;

public final class ProfileLevelService implements LevelService {

    private final ProfileService profiles;

    public ProfileLevelService(ProfileService profiles) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    public static int clamp(long level) {
        return (int) Math.clamp(level, 0L, LevelProgress.MAX_LEVEL);
    }

    @Override
    public Optional<LevelProgress> progress(UUID player) {
        return profiles.getCached(player).map(PlayerProfile::progress);
    }

    @Override
    public CompletableFuture<Optional<LevelProgress>> find(UUID player) {
        return profiles.find(player).thenApply(found -> found.map(PlayerProfile::progress));
    }

    @Override
    public CompletableFuture<LevelChange> addXp(UUID player, long amount, String reason) {
        if (amount < 0L) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("amount must not be negative"));
        }
        String cause = LevelService.reason(reason);
        return change(player, amount, cause, profile -> profile.addXp(amount, cause));
    }

    @Override
    public CompletableFuture<LevelChange> setLevel(UUID player, int level, String reason) {
        String cause = LevelService.reason(reason);
        int target = clamp(level);
        return change(player, 0L, cause, profile -> profile.setProgress(target, 0L, cause));
    }

    @Override
    public CompletableFuture<LevelChange> addLevels(UUID player, int levels, String reason) {
        String cause = LevelService.reason(reason);
        return change(player, 0L, cause,
                profile -> profile.setLevel(clamp((long) profile.getLevel() + levels), cause));
    }

    private CompletableFuture<LevelChange> change(UUID player, long xpAdded, String reason,
                                                  Consumer<PlayerProfile> action) {
        Objects.requireNonNull(player, "player");
        LevelProgress[] snapshots = new LevelProgress[2];
        return profiles.modify(player, profile -> {
            snapshots[0] = profile.progress();
            action.accept(profile);
            snapshots[1] = profile.progress();
        }).thenApply(profile -> new LevelChange(player, snapshots[0], snapshots[1], xpAdded, reason));
    }
}
