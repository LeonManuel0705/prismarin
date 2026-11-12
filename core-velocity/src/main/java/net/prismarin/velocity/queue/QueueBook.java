package net.prismarin.velocity.queue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import net.prismarin.api.queue.QueueEntry;
import net.prismarin.api.queue.QueueLeftEvent;
import net.prismarin.api.queue.QueuePosition;
import net.prismarin.api.queue.QueueStatus;
import net.prismarin.api.queue.QueueTarget;

public final class QueueBook {

    public record Slot(String server, int free, int rank, int players) {

        public static final int UNLIMITED = -1;

        public boolean unlimited() {
            return free < 0;
        }
    }

    public record Assignment(QueueTarget target, UUID owner, List<UUID> party, String server, int priority,
                             long joinedAt) {

        public Assignment {
            party = List.copyOf(party);
        }

        public List<UUID> players() {
            List<UUID> players = new ArrayList<>(party.size() + 1);
            players.add(owner);
            players.addAll(party);
            return List.copyOf(players);
        }
    }

    public record Removal(QueueTarget target, UUID player, List<UUID> party, QueueLeftEvent.Reason reason) {

        public Removal {
            party = List.copyOf(party);
        }

        public List<UUID> players() {
            List<UUID> players = new ArrayList<>(party.size() + 1);
            players.add(player);
            players.addAll(party);
            return List.copyOf(players);
        }
    }

    public record Joined(QueueStatus status, QueuePosition position, List<Removal> removed) {

        public Joined {
            removed = List.copyOf(removed);
        }
    }

    public record Flight(QueueTarget target, UUID owner, List<UUID> players, String server, long since) {

        public Flight {
            players = List.copyOf(players);
        }
    }

    private static final Comparator<Entry> ORDER = Comparator.comparingInt((Entry entry) -> -entry.priority)
            .thenComparingLong(entry -> entry.joinedAt)
            .thenComparingLong(entry -> entry.sequence);

    private static final Comparator<Slot> SLOT_ORDER = Comparator.comparingInt(Slot::rank)
            .thenComparing(Comparator.comparingInt(Slot::players).reversed())
            .thenComparing(Slot::server, String.CASE_INSENSITIVE_ORDER);

    private static final class Entry {

        private final QueueTarget target;
        private final UUID owner;
        private final List<UUID> party;
        private final int priority;
        private final long joinedAt;
        private final long sequence;
        private long notBefore;
        private int failures;

        private Entry(QueueTarget target, UUID owner, List<UUID> party, int priority, long joinedAt, long sequence) {
            this.target = target;
            this.owner = owner;
            this.party = new ArrayList<>(party);
            this.priority = priority;
            this.joinedAt = joinedAt;
            this.sequence = sequence;
        }

        private int size() {
            return party.size() + 1;
        }

        private boolean contains(UUID player) {
            return owner.equals(player) || party.contains(player);
        }

        private List<UUID> players() {
            List<UUID> players = new ArrayList<>(size());
            players.add(owner);
            players.addAll(party);
            return players;
        }
    }

    private record InFlight(Entry entry, String server, long since) {
    }

    private final Map<QueueTarget, TreeSet<Entry>> queues = new LinkedHashMap<>();
    private final Set<QueueTarget> paused = new LinkedHashSet<>();
    private final Map<UUID, Long> offline = new HashMap<>();
    private final Map<UUID, InFlight> flights = new LinkedHashMap<>();
    private long sequence;

    public synchronized Joined join(QueueTarget target, UUID owner, List<UUID> party, int priority,
                                    boolean onePerPlayer, long now) {
        for (Entry entry : entriesOf(owner)) {
            if (entry.target.equals(target)) {
                return new Joined(QueueStatus.ALREADY_QUEUED, position(entry), List.of());
            }
        }
        List<UUID> members = new ArrayList<>();
        for (UUID member : party) {
            if (!member.equals(owner) && !members.contains(member)) {
                members.add(member);
            }
        }
        List<Removal> removed = new ArrayList<>();
        List<UUID> unit = new ArrayList<>(members.size() + 1);
        unit.add(owner);
        unit.addAll(members);
        for (UUID player : unit) {
            for (Entry entry : entriesOf(player)) {
                if (onePerPlayer || entry.target.equals(target)) {
                    removed.add(removePlayer(entry, player, QueueLeftEvent.Reason.OTHER_QUEUE));
                }
            }
        }
        Entry entry = new Entry(target, owner, members, priority, now, ++sequence);
        queues.computeIfAbsent(target, key -> new TreeSet<>(ORDER)).add(entry);
        return new Joined(QueueStatus.QUEUED, position(entry), removed);
    }

    public synchronized List<Removal> leave(UUID player, QueueTarget target, QueueLeftEvent.Reason reason) {
        List<Removal> removed = new ArrayList<>();
        for (Entry entry : entriesOf(player)) {
            if (target == null || entry.target.equals(target)) {
                removed.add(removePlayer(entry, player, reason));
            }
        }
        if (entriesOf(player).isEmpty()) {
            offline.remove(player);
        }
        return removed;
    }

    public synchronized List<Removal> prune(BiPredicate<UUID, UUID> keep, QueueLeftEvent.Reason reason) {
        List<Removal> removed = new ArrayList<>();
        for (TreeSet<Entry> queue : queues.values()) {
            for (Entry entry : queue) {
                Iterator<UUID> members = entry.party.iterator();
                while (members.hasNext()) {
                    UUID member = members.next();
                    if (!keep.test(entry.owner, member)) {
                        members.remove();
                        removed.add(new Removal(entry.target, member, List.of(), reason));
                    }
                }
            }
        }
        return removed;
    }

    public synchronized List<QueuePosition> positions(UUID player) {
        List<QueuePosition> positions = new ArrayList<>();
        for (Entry entry : entriesOf(player)) {
            positions.add(position(entry));
        }
        positions.sort(Comparator.comparingInt(QueuePosition::position)
                .thenComparing(position -> position.target().id()));
        return List.copyOf(positions);
    }

    public synchronized Optional<QueuePosition> position(UUID player) {
        return positions(player).stream().findFirst();
    }

    public synchronized Map<UUID, QueuePosition> shown() {
        Map<UUID, QueuePosition> shown = new LinkedHashMap<>();
        for (TreeSet<Entry> queue : queues.values()) {
            for (Entry entry : queue) {
                QueuePosition position = position(entry);
                for (UUID player : entry.players()) {
                    shown.merge(player, position, (old, now) -> now.position() < old.position() ? now : old);
                }
            }
        }
        return shown;
    }

    public synchronized boolean queued(UUID player) {
        return !entriesOf(player).isEmpty();
    }

    public synchronized boolean holds(QueueTarget target) {
        if (paused.contains(target)) {
            return true;
        }
        TreeSet<Entry> queue = queues.get(target);
        if (queue == null) {
            return false;
        }
        for (Entry entry : queue) {
            if (!offline.containsKey(entry.owner)) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean pause(QueueTarget target) {
        return paused.add(target);
    }

    public synchronized boolean resume(QueueTarget target) {
        boolean changed = paused.remove(target);
        TreeSet<Entry> queue = queues.get(target);
        if (queue != null && queue.isEmpty()) {
            queues.remove(target);
        }
        return changed;
    }

    public synchronized boolean paused(QueueTarget target) {
        return paused.contains(target);
    }

    public synchronized Set<QueueTarget> pausedTargets() {
        return Set.copyOf(paused);
    }

    public synchronized List<Removal> clear(QueueTarget target, QueueLeftEvent.Reason reason) {
        TreeSet<Entry> queue = queues.remove(target);
        if (queue == null) {
            return List.of();
        }
        List<Removal> removed = new ArrayList<>();
        for (Entry entry : queue) {
            removed.add(new Removal(target, entry.owner, entry.party, reason));
            if (entriesOf(entry.owner).isEmpty()) {
                offline.remove(entry.owner);
            }
        }
        return removed;
    }

    public synchronized List<Removal> offline(UUID player, long now) {
        List<Removal> removed = new ArrayList<>();
        boolean owner = false;
        for (Entry entry : entriesOf(player)) {
            if (entry.owner.equals(player)) {
                owner = true;
            } else {
                removed.add(removePlayer(entry, player, QueueLeftEvent.Reason.DISCONNECTED));
            }
        }
        if (owner) {
            offline.putIfAbsent(player, now);
        }
        return removed;
    }

    public synchronized boolean away(UUID player) {
        return offline.containsKey(player);
    }

    public synchronized List<QueuePosition> online(UUID player) {
        if (offline.remove(player) == null) {
            return List.of();
        }
        return positions(player);
    }

    public synchronized List<Removal> expire(long now, long graceMillis) {
        List<Removal> removed = new ArrayList<>();
        Iterator<Map.Entry<UUID, Long>> away = offline.entrySet().iterator();
        while (away.hasNext()) {
            Map.Entry<UUID, Long> entry = away.next();
            if (entry.getValue() + graceMillis > now) {
                continue;
            }
            away.remove();
            for (Entry owned : entriesOf(entry.getKey())) {
                if (owned.owner.equals(entry.getKey())) {
                    removed.add(removePlayer(owned, owned.owner, QueueLeftEvent.Reason.DISCONNECTED));
                }
            }
        }
        return removed;
    }

    public synchronized List<Assignment> plan(Function<QueueTarget, List<Slot>> slots, Predicate<QueueTarget> hold,
                                              long now) {
        Map<String, Integer> free = new HashMap<>();
        Map<QueueTarget, List<Slot>> options = new HashMap<>();
        List<QueueTarget> active = new ArrayList<>();
        for (QueueTarget target : queues.keySet()) {
            if (!paused.contains(target)) {
                active.add(target);
            }
        }
        List<Assignment> assignments = new ArrayList<>();
        while (!active.isEmpty()) {
            Entry best = null;
            Slot bestSlot = null;
            Iterator<QueueTarget> targets = active.iterator();
            while (targets.hasNext()) {
                QueueTarget target = targets.next();
                TreeSet<Entry> queue = queues.get(target);
                if (queue == null || queue.isEmpty()) {
                    targets.remove();
                    continue;
                }
                List<Slot> available = options.computeIfAbsent(target, slots);
                boolean holding = hold.test(target);
                Entry candidate = null;
                Slot candidateSlot = null;
                for (Entry entry : queue) {
                    if (!eligible(entry, now)) {
                        continue;
                    }
                    Slot slot = fitting(available, free, entry.size());
                    if (slot != null) {
                        candidate = entry;
                        candidateSlot = slot;
                        break;
                    }
                    if (holding) {
                        break;
                    }
                }
                if (candidate == null) {
                    targets.remove();
                    continue;
                }
                if (best == null || ORDER.compare(candidate, best) < 0) {
                    best = candidate;
                    bestSlot = candidateSlot;
                }
            }
            if (best == null) {
                break;
            }
            int left = free.get(bestSlot.server());
            if (left != Integer.MAX_VALUE) {
                free.put(bestSlot.server(), left - best.size());
            }
            assignments.add(take(best, bestSlot.server(), now));
        }
        return assignments;
    }

    public synchronized Optional<Assignment> force(QueueTarget target, List<Slot> slots, long now) {
        TreeSet<Entry> queue = queues.get(target);
        if (queue == null || slots.isEmpty()) {
            return Optional.empty();
        }
        Slot slot = slots.stream().min(SLOT_ORDER).orElseThrow();
        for (Entry entry : queue) {
            if (!offline.containsKey(entry.owner) && !flights.containsKey(entry.owner)) {
                return Optional.of(take(entry, slot.server(), now));
            }
        }
        return Optional.empty();
    }

    public synchronized List<Removal> landed(UUID owner) {
        InFlight flight = flights.remove(owner);
        if (flight == null) {
            return List.of();
        }
        List<Removal> removed = new ArrayList<>();
        for (UUID player : flight.entry().players()) {
            for (Entry entry : entriesOf(player)) {
                removed.add(removePlayer(entry, player, QueueLeftEvent.Reason.SENT_ELSEWHERE));
            }
            if (entriesOf(player).isEmpty()) {
                offline.remove(player);
            }
        }
        return removed;
    }

    public synchronized Optional<Removal> failed(UUID owner, long now, long retryMillis, int maxAttempts) {
        InFlight flight = flights.remove(owner);
        if (flight == null) {
            return Optional.empty();
        }
        Entry entry = flight.entry();
        entry.failures++;
        if (entry.failures >= maxAttempts || !entriesOf(owner).isEmpty()) {
            return Optional.of(new Removal(entry.target, owner, entry.party, QueueLeftEvent.Reason.FAILED));
        }
        entry.notBefore = now + retryMillis;
        queues.computeIfAbsent(entry.target, key -> new TreeSet<>(ORDER)).add(entry);
        return Optional.empty();
    }

    public synchronized boolean retry(UUID owner, long now, long delayMillis) {
        InFlight flight = flights.remove(owner);
        if (flight == null || !entriesOf(owner).isEmpty()) {
            return false;
        }
        Entry entry = flight.entry();
        entry.notBefore = now + delayMillis;
        queues.computeIfAbsent(entry.target, key -> new TreeSet<>(ORDER)).add(entry);
        return true;
    }

    public synchronized List<Flight> stale(long now, long timeoutMillis) {
        List<Flight> stale = new ArrayList<>();
        for (InFlight flight : flights.values()) {
            if (flight.since() + timeoutMillis <= now) {
                stale.add(flight(flight));
            }
        }
        return stale;
    }

    public synchronized Optional<Flight> flightOf(UUID player) {
        for (InFlight flight : flights.values()) {
            if (flight.entry().contains(player)) {
                return Optional.of(flight(flight));
            }
        }
        return Optional.empty();
    }

    public synchronized int inFlight(String server) {
        int players = 0;
        for (InFlight flight : flights.values()) {
            if (flight.server().equalsIgnoreCase(server)) {
                players += flight.entry().size();
            }
        }
        return players;
    }

    public synchronized List<QueueTarget> targets() {
        Set<QueueTarget> targets = new LinkedHashSet<>(queues.keySet());
        targets.addAll(paused);
        return List.copyOf(targets);
    }

    public synchronized boolean empty() {
        return queues.isEmpty() && flights.isEmpty();
    }

    public synchronized int size(QueueTarget target) {
        TreeSet<Entry> queue = queues.get(target);
        return queue == null ? 0 : queue.size();
    }

    public synchronized int players(QueueTarget target) {
        TreeSet<Entry> queue = queues.get(target);
        if (queue == null) {
            return 0;
        }
        int players = 0;
        for (Entry entry : queue) {
            players += entry.size();
        }
        return players;
    }

    public synchronized List<QueueEntry> entries(QueueTarget target, Function<UUID, String> names) {
        TreeSet<Entry> queue = queues.get(target);
        if (queue == null) {
            return List.of();
        }
        List<QueueEntry> entries = new ArrayList<>(queue.size());
        int position = 0;
        for (Entry entry : queue) {
            position++;
            entries.add(new QueueEntry(entry.owner, names.apply(entry.owner), entry.party, position,
                    entry.priority, entry.joinedAt, !offline.containsKey(entry.owner)));
        }
        return List.copyOf(entries);
    }

    private Slot fitting(List<Slot> available, Map<String, Integer> free, int size) {
        Slot best = null;
        for (Slot slot : available) {
            int left = free.computeIfAbsent(slot.server(), server -> slot.unlimited() ? Integer.MAX_VALUE
                    : Math.max(0, slot.free() - inFlight(server)));
            if (left >= size && (best == null || SLOT_ORDER.compare(slot, best) < 0)) {
                best = slot;
            }
        }
        return best;
    }

    private boolean eligible(Entry entry, long now) {
        return !offline.containsKey(entry.owner) && entry.notBefore <= now && !flights.containsKey(entry.owner);
    }

    private Assignment take(Entry entry, String server, long now) {
        detach(entry);
        flights.put(entry.owner, new InFlight(entry, server, now));
        return new Assignment(entry.target, entry.owner, entry.party, server, entry.priority, entry.joinedAt);
    }

    private Removal removePlayer(Entry entry, UUID player, QueueLeftEvent.Reason reason) {
        if (entry.owner.equals(player)) {
            detach(entry);
            return new Removal(entry.target, player, entry.party, reason);
        }
        entry.party.remove(player);
        return new Removal(entry.target, player, List.of(), reason);
    }

    private void detach(Entry entry) {
        TreeSet<Entry> queue = queues.get(entry.target);
        if (queue == null) {
            return;
        }
        queue.remove(entry);
        if (queue.isEmpty()) {
            queues.remove(entry.target);
        }
    }

    private List<Entry> entriesOf(UUID player) {
        List<Entry> found = new ArrayList<>();
        for (TreeSet<Entry> queue : queues.values()) {
            for (Entry entry : queue) {
                if (entry.contains(player)) {
                    found.add(entry);
                }
            }
        }
        return found;
    }

    private QueuePosition position(Entry entry) {
        TreeSet<Entry> queue = queues.get(entry.target);
        int position = 0;
        int index = 0;
        int players = 0;
        if (queue != null) {
            for (Entry other : queue) {
                index++;
                players += other.size();
                if (other == entry) {
                    position = index;
                }
            }
        }
        return new QueuePosition(entry.target, entry.owner, entry.party, position, queue == null ? 0 : queue.size(),
                players, entry.priority, paused.contains(entry.target), entry.joinedAt,
                !offline.containsKey(entry.owner));
    }

    private static Flight flight(InFlight flight) {
        return new Flight(flight.entry().target, flight.entry().owner, flight.entry().players(), flight.server(),
                flight.since());
    }
}
