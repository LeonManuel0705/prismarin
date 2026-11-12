package net.prismarin.api.stats;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record Leaderboard(String key, StatPeriod period, String periodId, List<LeaderboardEntry> entries,
                          long updatedAt) {

    public Leaderboard {
        key = StatKeys.key(key);
        Objects.requireNonNull(period, "period");
        periodId = periodId == null ? "" : periodId;
        entries = List.copyOf(entries);
    }

    public Optional<LeaderboardEntry> entry(int position) {
        return position < 1 || position > entries.size() ? Optional.empty() : Optional.of(entries.get(position - 1));
    }

    public Leaderboard limit(int size) {
        if (size < 0 || size >= entries.size()) {
            return this;
        }
        return new Leaderboard(key, period, periodId, entries.subList(0, size), updatedAt);
    }
}
