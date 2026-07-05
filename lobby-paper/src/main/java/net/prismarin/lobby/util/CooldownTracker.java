package net.prismarin.lobby.util;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownTracker {

    private final long durationMillis;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public CooldownTracker(Duration duration) {
        this.durationMillis = duration.toMillis();
    }

    public boolean tryUse(UUID id, long nowMillis) {
        Long last = lastUse.get(id);
        if (last != null && nowMillis - last < durationMillis) {
            return false;
        }
        lastUse.put(id, nowMillis);
        return true;
    }

    public long remainingMillis(UUID id, long nowMillis) {
        Long last = lastUse.get(id);
        if (last == null) {
            return 0L;
        }
        return Math.max(0L, durationMillis - (nowMillis - last));
    }

    public void clear(UUID id) {
        lastUse.remove(id);
    }
}
