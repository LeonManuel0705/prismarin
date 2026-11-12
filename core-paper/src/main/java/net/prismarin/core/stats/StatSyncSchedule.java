package net.prismarin.core.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StatSyncSchedule {

    private final long intervalMillis;
    private final long joinDelayMillis;
    private final Map<UUID, Long> due = new ConcurrentHashMap<>();

    public StatSyncSchedule(long intervalMillis, long joinDelayMillis) {
        this.intervalMillis = Math.max(0L, intervalMillis);
        this.joinDelayMillis = Math.max(0L, joinDelayMillis);
    }

    public static StatSyncSchedule of(StatsSettings.PlaceholderSync sync) {
        return new StatSyncSchedule(sync.minutes() * 60_000L, sync.joinDelaySeconds() * 1_000L);
    }

    public long intervalMillis() {
        return intervalMillis;
    }

    public void joined(UUID player, long now) {
        due.put(player, now + joinDelayMillis);
    }

    public void left(UUID player) {
        due.remove(player);
    }

    public OptionalLong next(UUID player) {
        Long at = due.get(player);
        return at == null || at == Long.MAX_VALUE ? OptionalLong.empty() : OptionalLong.of(at);
    }

    public List<UUID> due(long now) {
        List<UUID> ready = new ArrayList<>();
        due.forEach((player, at) -> {
            if (at <= now) {
                ready.add(player);
            }
        });
        for (UUID player : ready) {
            due.computeIfPresent(player, (key, at) -> intervalMillis > 0L ? now + intervalMillis : Long.MAX_VALUE);
        }
        return List.copyOf(ready);
    }

    public int size() {
        return due.size();
    }
}
