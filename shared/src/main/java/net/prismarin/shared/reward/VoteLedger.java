package net.prismarin.shared.reward;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.prismarin.api.reward.Reward;
import net.prismarin.api.reward.Vote;
import net.prismarin.api.reward.VoteStats;
import net.prismarin.shared.storage.DocumentStore;
import net.prismarin.shared.time.DayClock;
import net.prismarin.shared.time.Streaks;

public final class VoteLedger {

    public static final String PLAYERS = "vote-players";
    public static final String QUEUE = "vote-queue";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public record Counted(long total, int streak, int best, boolean newDay, int lostStreak) {
    }

    private static final class Voter {

        String player;
        String name;
        long total;
        int streak;
        int best;
        Long lastDay;
        long lastAt;
    }

    private static final class Queued {

        String id;
        String player;
        String name;
        @SerializedName("name-key")
        String nameKey;
        String service;
        String address;
        long at;
        int streak;
        Reward reward;

        Vote vote(UUID owner) {
            UUID uuid = owner;
            if (uuid == null && player != null && !player.isEmpty()) {
                try {
                    uuid = UUID.fromString(player);
                } catch (IllegalArgumentException e) {
                    uuid = null;
                }
            }
            return new Vote(id, uuid, name, service, address, at, streak, reward);
        }
    }

    private final DocumentStore documents;
    private final Clock clock;
    private final DayClock days;
    private final int graceDays;
    private final boolean streaks;

    public VoteLedger(DocumentStore documents, Clock clock, DayClock days, int graceDays, boolean streaks) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.days = Objects.requireNonNull(days, "days");
        this.graceDays = Math.max(0, graceDays);
        this.streaks = streaks;
    }

    public static String nameKey(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }

    public Counted count(UUID player, String name, long at) {
        Objects.requireNonNull(player, "player");
        long today = days.epochDay(Instant.ofEpochMilli(at));
        AtomicReference<Counted> counted = new AtomicReference<>();
        documents.update(PLAYERS, player.toString(), current -> {
            Voter voter = voter(current, player);
            long lastDay = voter.lastDay == null ? Streaks.NEVER : voter.lastDay;
            Streaks.Step step = streaks ? Streaks.next(lastDay, voter.streak, today, graceDays)
                    : new Streaks.Step(0, false, 0, 0);
            voter.total = voter.total == Long.MAX_VALUE ? voter.total : voter.total + 1L;
            voter.name = name == null || name.isBlank() ? voter.name : name;
            voter.lastAt = Math.max(voter.lastAt, at);
            if (streaks && step.counted()) {
                voter.streak = step.streak();
                voter.best = Math.max(voter.best, step.streak());
                voter.lastDay = today;
            }
            counted.set(new Counted(voter.total, voter.streak, voter.best, step.counted(), step.lost()));
            return GSON.toJson(voter);
        });
        return counted.get();
    }

    public void queue(Vote vote) {
        Queued queued = new Queued();
        queued.id = vote.id();
        queued.player = vote.player() == null ? "" : vote.player().toString();
        queued.name = vote.name();
        queued.nameKey = nameKey(vote.name());
        queued.service = vote.service();
        queued.address = vote.address();
        queued.at = vote.at();
        queued.streak = vote.streak();
        queued.reward = vote.reward();
        documents.save(QUEUE, vote.id(), GSON.toJson(queued));
    }

    public List<Vote> pending(UUID player, String name) {
        return found(player, name).values().stream()
                .filter(queued -> owned(queued, player))
                .map(queued -> queued.vote(player))
                .sorted(Comparator.comparingLong(Vote::at))
                .toList();
    }

    public List<Vote> claim(UUID player, String name, long maxAgeMillis, int limit, int budgetBytes) {
        long now = clock.millis();
        List<Queued> candidates = new ArrayList<>(found(player, name).values());
        candidates.sort(Comparator.comparingLong(queued -> queued.at));
        List<Vote> claimed = new ArrayList<>();
        int used = 0;
        for (Queued queued : candidates) {
            if (claimed.size() >= Math.max(0, limit)) {
                break;
            }
            if (!owned(queued, player)) {
                continue;
            }
            boolean expired = maxAgeMillis > 0L && now - queued.at > maxAgeMillis;
            Vote vote = queued.vote(player);
            int size = expired ? 0 : GSON.toJson(vote).getBytes(StandardCharsets.UTF_8).length + 1;
            if (!expired && !claimed.isEmpty() && used + size > budgetBytes) {
                break;
            }
            if (!documents.delete(QUEUE, queued.id) || expired) {
                continue;
            }
            used += size;
            claimed.add(vote);
        }
        claimed.sort(Comparator.comparingLong(Vote::at));
        return List.copyOf(claimed);
    }

    public VoteStats stats(UUID player) {
        Objects.requireNonNull(player, "player");
        Voter voter = voter(documents.find(PLAYERS, player.toString()).orElse(null), player);
        long today = days.epochDay(clock.instant());
        long lastDay = voter.lastDay == null ? Streaks.NEVER : voter.lastDay;
        int streak = streaks ? Streaks.current(lastDay, voter.streak, today, graceDays) : 0;
        int pending = (int) found(player, voter.name).values().stream()
                .filter(queued -> owned(queued, player))
                .count();
        return new VoteStats(player, voter.total, streak, voter.best, voter.lastAt, pending);
    }

    public int prune(long maxAgeMillis) {
        if (maxAgeMillis <= 0L) {
            return 0;
        }
        long now = clock.millis();
        int removed = 0;
        for (Map.Entry<String, String> entry : documents.entries(QUEUE).entrySet()) {
            Queued queued = queued(entry.getValue());
            if ((queued == null || now - queued.at > maxAgeMillis) && documents.delete(QUEUE, entry.getKey())) {
                removed++;
            }
        }
        return removed;
    }

    private Map<String, Queued> found(UUID player, String name) {
        Map<String, Queued> found = new LinkedHashMap<>();
        if (player != null) {
            documents.findBy(QUEUE, "player", player.toString()).forEach(json -> add(found, json));
        }
        String key = nameKey(name);
        if (!key.isEmpty()) {
            documents.findBy(QUEUE, "name-key", key).forEach(json -> add(found, json));
        }
        return found;
    }

    private static boolean owned(Queued queued, UUID player) {
        return queued.player == null || queued.player.isEmpty() || player != null
                && queued.player.equals(player.toString());
    }

    private static void add(Map<String, Queued> found, String json) {
        Queued queued = queued(json);
        if (queued != null && queued.id != null) {
            found.putIfAbsent(queued.id, queued);
        }
    }

    private static Queued queued(String json) {
        try {
            return GSON.fromJson(json, Queued.class);
        } catch (JsonParseException e) {
            return null;
        }
    }

    private static Voter voter(String json, UUID player) {
        Voter voter = null;
        if (json != null && !json.isBlank()) {
            try {
                voter = GSON.fromJson(json, Voter.class);
            } catch (JsonParseException e) {
                voter = null;
            }
        }
        if (voter == null) {
            voter = new Voter();
        }
        voter.player = player.toString();
        voter.name = voter.name == null ? "" : voter.name;
        return voter;
    }
}
