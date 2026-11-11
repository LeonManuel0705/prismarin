package net.prismarin.api.level;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface LevelService {

    String REASON_PLUGIN = "plugin";
    String REASON_COMMAND = "command";

    static String reason(String reason) {
        return reason == null || reason.isBlank() ? REASON_PLUGIN : reason.strip();
    }

    default long requirement(int level) {
        return LevelProgress.requirement(level);
    }

    Optional<LevelProgress> progress(UUID player);

    CompletableFuture<Optional<LevelProgress>> find(UUID player);

    CompletableFuture<LevelChange> addXp(UUID player, long amount, String reason);

    default CompletableFuture<LevelChange> addXp(UUID player, long amount) {
        return addXp(player, amount, REASON_PLUGIN);
    }

    CompletableFuture<LevelChange> setLevel(UUID player, int level, String reason);

    CompletableFuture<LevelChange> addLevels(UUID player, int levels, String reason);
}
