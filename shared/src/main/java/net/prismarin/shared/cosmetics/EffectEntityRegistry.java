package net.prismarin.shared.cosmetics;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EffectEntityRegistry {

    private final Map<UUID, Long> entities = new ConcurrentHashMap<>();
    private volatile int max;

    public EffectEntityRegistry(int max) {
        this.max = Math.max(1, max);
    }

    public void max(int value) {
        max = Math.max(1, value);
    }

    public boolean room(int count) {
        return entities.size() + Math.max(0, count) <= max;
    }

    public boolean mark(UUID entity, long run) {
        Objects.requireNonNull(entity, "entity");
        if (entities.containsKey(entity)) {
            return true;
        }
        if (!room(1)) {
            return false;
        }
        entities.put(entity, run);
        return true;
    }

    public void adopt(UUID entity, long run) {
        entities.put(Objects.requireNonNull(entity, "entity"), run);
    }

    public boolean marked(UUID entity) {
        return entity != null && entities.containsKey(entity);
    }

    public boolean release(UUID entity) {
        return entity != null && entities.remove(entity) != null;
    }

    public List<UUID> releaseRun(long run) {
        List<UUID> released = entities.entrySet().stream().filter(entry -> entry.getValue() == run)
                .map(Map.Entry::getKey).toList();
        released.forEach(entities::remove);
        return released;
    }

    public int size() {
        return entities.size();
    }

    public void clear() {
        entities.clear();
    }
}
