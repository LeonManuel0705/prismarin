package net.prismarin.velocity.joinme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.joinme.JoinMe;

public final class PendingArrivals {

    public record Pending(UUID player, JoinMe joinMe, boolean party, boolean reserved, long expiresAt) {

        public String server() {
            return joinMe.server();
        }
    }

    private final Map<UUID, Pending> pending = new LinkedHashMap<>();

    public synchronized Optional<Pending> expect(Pending arrival) {
        return Optional.ofNullable(pending.put(arrival.player(), arrival));
    }

    public synchronized Optional<Pending> take(UUID player, String server, long now) {
        Pending found = pending.get(player);
        if (found == null) {
            return Optional.empty();
        }
        if (found.expiresAt() <= now) {
            return Optional.empty();
        }
        if (!found.server().equalsIgnoreCase(server)) {
            return Optional.empty();
        }
        pending.remove(player);
        return Optional.of(found);
    }

    public synchronized Optional<Pending> forget(UUID player) {
        return Optional.ofNullable(pending.remove(player));
    }

    public synchronized List<Pending> expired(long now) {
        List<Pending> gone = new ArrayList<>();
        pending.values().removeIf(arrival -> {
            if (arrival.expiresAt() <= now) {
                gone.add(arrival);
                return true;
            }
            return false;
        });
        return gone;
    }

    public synchronized int size() {
        return pending.size();
    }
}
