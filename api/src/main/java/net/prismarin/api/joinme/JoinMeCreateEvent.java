package net.prismarin.api.joinme;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import net.prismarin.api.event.Cancellable;
import net.prismarin.api.event.PrismarinEvent;

public final class JoinMeCreateEvent implements PrismarinEvent, Cancellable {

    private final JoinMeSpot spot;
    private final Map<String, String> placeholders;
    private final Map<Integer, String> lines;
    private volatile Duration lifetime;
    private volatile int maxJoins;
    private volatile JoinMeAudience audience;
    private volatile boolean cancelled;

    public JoinMeCreateEvent(JoinMeSpot spot, Map<String, String> placeholders, Duration lifetime, int maxJoins,
                             JoinMeAudience audience) {
        this(spot, placeholders, Map.of(), lifetime, maxJoins, audience);
    }

    public JoinMeCreateEvent(JoinMeSpot spot, Map<String, String> placeholders, Map<Integer, String> lines,
                             Duration lifetime, int maxJoins, JoinMeAudience audience) {
        this.spot = Objects.requireNonNull(spot, "spot");
        this.placeholders = Collections.synchronizedMap(new LinkedHashMap<>(placeholders));
        this.lines = Collections.synchronizedMap(new TreeMap<>(lines));
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        this.maxJoins = Math.max(0, maxJoins);
        this.audience = audience == null ? JoinMeAudience.NETWORK : audience;
    }

    public UUID creator() {
        return spot.creator();
    }

    public JoinMeSpot spot() {
        return spot;
    }

    public Map<String, String> placeholders() {
        return placeholders;
    }

    public void placeholder(String key, String value) {
        placeholders.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
    }

    public Map<Integer, String> lines() {
        return lines;
    }

    public void line(int row, String text) {
        if (row < 1) {
            throw new IllegalArgumentException("rows start at 1");
        }
        lines.put(row, Objects.requireNonNull(text, "text"));
    }

    public Duration lifetime() {
        return lifetime;
    }

    public void lifetime(Duration value) {
        if (value != null && !value.isNegative() && !value.isZero()) {
            lifetime = value;
        }
    }

    public int maxJoins() {
        return maxJoins;
    }

    public void maxJoins(int value) {
        maxJoins = Math.max(0, value);
    }

    public JoinMeAudience audience() {
        return audience;
    }

    public void audience(JoinMeAudience value) {
        if (value != null) {
            audience = value;
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
