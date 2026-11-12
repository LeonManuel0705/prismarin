package net.prismarin.velocity.joinme;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeResult;
import net.prismarin.shared.joinme.JoinMeLook;

public final class JoinMeRegistry {

    public static final int ID_LENGTH = 6;
    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";

    public record Entry(JoinMe joinMe, boolean worldFallback, int reserved, JoinMeLook look) {

        public Entry {
            look = look == null ? JoinMeLook.NONE : look;
        }

        public boolean full() {
            return joinMe.maxJoins() > 0 && joinMe.joins() + reserved >= joinMe.maxJoins();
        }
    }

    private final Map<String, Entry> active = new LinkedHashMap<>();
    private final Map<UUID, Long> stamps = new LinkedHashMap<>();
    private final Random random;

    public JoinMeRegistry() {
        this(new SecureRandom());
    }

    public JoinMeRegistry(Random random) {
        this.random = random;
    }

    public synchronized String newId() {
        while (true) {
            StringBuilder id = new StringBuilder(ID_LENGTH);
            for (int index = 0; index < ID_LENGTH; index++) {
                id.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            if (!active.containsKey(id.toString())) {
                return id.toString();
            }
        }
    }

    public synchronized void put(JoinMe joinMe, boolean worldFallback) {
        put(joinMe, worldFallback, JoinMeLook.NONE);
    }

    public synchronized void put(JoinMe joinMe, boolean worldFallback, JoinMeLook look) {
        Entry previous = active.get(joinMe.id());
        active.put(joinMe.id(), new Entry(joinMe, worldFallback, previous == null ? 0 : previous.reserved(), look));
    }

    public synchronized Optional<Entry> get(String id) {
        return Optional.ofNullable(id == null ? null : active.get(id));
    }

    public synchronized Optional<Entry> byCreator(UUID creator) {
        return active.values().stream().filter(entry -> entry.joinMe().creator().equals(creator)).findFirst();
    }

    public synchronized List<JoinMe> all() {
        return active.values().stream().map(Entry::joinMe)
                .sorted(Comparator.comparingLong(JoinMe::createdAt).reversed()).toList();
    }

    public synchronized Optional<JoinMe> remove(String id) {
        Entry removed = active.remove(id);
        return Optional.ofNullable(removed == null ? null : removed.joinMe());
    }

    public synchronized JoinMeResult.Status reserve(String id, long now) {
        Entry entry = active.get(id);
        if (entry == null) {
            return JoinMeResult.Status.NOT_FOUND;
        }
        if (entry.joinMe().expired(now)) {
            return JoinMeResult.Status.EXPIRED;
        }
        if (entry.full()) {
            return JoinMeResult.Status.USED_UP;
        }
        active.put(id, new Entry(entry.joinMe(), entry.worldFallback(), entry.reserved() + 1, entry.look()));
        return JoinMeResult.Status.SUCCESS;
    }

    public synchronized void release(String id) {
        Entry entry = active.get(id);
        if (entry != null && entry.reserved() > 0) {
            active.put(id, new Entry(entry.joinMe(), entry.worldFallback(), entry.reserved() - 1, entry.look()));
        }
    }

    public synchronized Optional<JoinMe> arrived(String id, boolean reserved) {
        Entry entry = active.get(id);
        if (entry == null) {
            return Optional.empty();
        }
        JoinMe updated = entry.joinMe().withJoins(entry.joinMe().joins() + 1);
        active.put(id, new Entry(updated, entry.worldFallback(),
                reserved ? Math.max(0, entry.reserved() - 1) : entry.reserved(), entry.look()));
        return Optional.of(updated);
    }

    public synchronized Optional<JoinMe> addJoins(String id, int delta) {
        Entry entry = active.get(id);
        if (entry == null || delta <= 0) {
            return Optional.empty();
        }
        JoinMe updated = entry.joinMe().withJoins(entry.joinMe().joins() + delta);
        active.put(id, new Entry(updated, entry.worldFallback(), entry.reserved(), entry.look()));
        return Optional.of(updated);
    }

    public synchronized Optional<JoinMe> moved(String id, String world) {
        Entry entry = active.get(id);
        if (entry == null || world == null || world.isEmpty() || world.equals(entry.joinMe().world())) {
            return Optional.empty();
        }
        JoinMe updated = entry.joinMe().withWorld(world);
        active.put(id, new Entry(updated, entry.worldFallback(), entry.reserved(), entry.look()));
        return Optional.of(updated);
    }

    public synchronized List<JoinMe> expire(long now) {
        List<JoinMe> ended = new ArrayList<>();
        active.values().removeIf(entry -> {
            if (entry.joinMe().expired(now)) {
                ended.add(entry.joinMe());
                return true;
            }
            return false;
        });
        return ended;
    }

    public synchronized long cooldownLeft(UUID creator, long cooldownMillis, long now) {
        Long last = stamps.get(creator);
        if (last == null || cooldownMillis <= 0L) {
            return 0L;
        }
        return Math.max(0L, last + cooldownMillis - now);
    }

    public synchronized void stamp(UUID creator, long at) {
        Long known = stamps.get(creator);
        if (known == null || at > known) {
            stamps.put(creator, at);
        }
    }

    public synchronized void forgetStamps(long before) {
        stamps.values().removeIf(stamp -> stamp < before);
    }

    public synchronized int size() {
        return active.size();
    }
}
