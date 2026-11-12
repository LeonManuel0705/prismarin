package net.prismarin.shared.reward;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.prismarin.api.reward.DailyStatus;
import net.prismarin.shared.storage.DocumentStore;
import net.prismarin.shared.time.DayClock;
import net.prismarin.shared.time.Streaks;

public final class DailyLedger {

    public static final String COLLECTION = "daily-rewards";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public record State(int streak, int best, long claims, long lastDay, long lastAt) {

        public static final State NONE = new State(0, 0, 0L, Streaks.NEVER, 0L);
    }

    public record Outcome(boolean claimed, State before, State after, Streaks.Step step, DailyStatus status) {
    }

    private static final class Stored {

        String player;
        String name;
        int streak;
        int best;
        long claims;
        Long lastDay;
        long lastAt;
    }

    private final DocumentStore documents;
    private final Clock clock;
    private final DayClock days;
    private final int graceDays;

    public DailyLedger(DocumentStore documents, Clock clock, DayClock days, int graceDays) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.days = Objects.requireNonNull(days, "days");
        this.graceDays = Math.max(0, graceDays);
    }

    public Outcome claim(UUID player, String name) {
        Objects.requireNonNull(player, "player");
        Instant now = clock.instant();
        long today = days.epochDay(now);
        AtomicReference<Outcome> outcome = new AtomicReference<>();
        documents.update(COLLECTION, player.toString(), current -> {
            Stored stored = decode(current, player);
            State before = state(stored);
            Streaks.Step step = Streaks.next(before.lastDay(), before.streak(), today, graceDays);
            if (!step.counted()) {
                outcome.set(new Outcome(false, before, before, step, status(player, before, now)));
                return null;
            }
            stored.name = name == null || name.isBlank() ? stored.name : name;
            stored.streak = step.streak();
            stored.best = Math.max(stored.best, step.streak());
            stored.claims = stored.claims == Long.MAX_VALUE ? stored.claims : stored.claims + 1L;
            stored.lastDay = today;
            stored.lastAt = now.toEpochMilli();
            State after = state(stored);
            outcome.set(new Outcome(true, before, after, step, status(player, after, now)));
            return GSON.toJson(stored);
        });
        return outcome.get();
    }

    public State state(UUID player) {
        return state(decode(documents.find(COLLECTION, player.toString()).orElse(null), player));
    }

    public DailyStatus status(UUID player, State state) {
        return status(player, state, clock.instant());
    }

    private DailyStatus status(UUID player, State state, Instant now) {
        long today = days.epochDay(now);
        int current = Streaks.current(state.lastDay(), state.streak(), today, graceDays);
        boolean claimable = state.lastDay() == Streaks.NEVER || state.lastDay() < today;
        int next = Streaks.next(state.lastDay(), state.streak(), claimable ? today : today + 1L, graceDays).streak();
        return new DailyStatus(player, current, state.best(), state.claims(), claimable,
                state.lastAt(), days.nextReset(now).toEpochMilli(), next);
    }

    private static State state(Stored stored) {
        return new State(Math.max(0, stored.streak), Math.max(0, stored.best), Math.max(0L, stored.claims),
                stored.lastDay == null ? Streaks.NEVER : stored.lastDay, stored.lastAt);
    }

    private static Stored decode(String json, UUID player) {
        Stored stored = null;
        if (json != null && !json.isBlank()) {
            try {
                stored = GSON.fromJson(json, Stored.class);
            } catch (JsonParseException e) {
                stored = null;
            }
        }
        if (stored == null) {
            stored = new Stored();
        }
        stored.player = player.toString();
        stored.name = stored.name == null ? "" : stored.name;
        return stored;
    }
}
