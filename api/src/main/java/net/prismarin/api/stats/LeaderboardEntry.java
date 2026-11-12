package net.prismarin.api.stats;

import java.util.Objects;
import java.util.UUID;

public record LeaderboardEntry(int rank, UUID player, String name, long value) {

    public LeaderboardEntry {
        Objects.requireNonNull(player, "player");
        name = name == null ? "" : name;
    }
}
