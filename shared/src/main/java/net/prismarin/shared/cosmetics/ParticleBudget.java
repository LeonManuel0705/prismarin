package net.prismarin.shared.cosmetics;

import java.util.Objects;
import java.util.function.LongSupplier;

public final class ParticleBudget {

    private final LongSupplier clock;
    private int max;
    private long tick = Long.MIN_VALUE;
    private int spent;

    public ParticleBudget(int max, LongSupplier clock) {
        this.max = Math.max(1, max);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void max(int value) {
        max = Math.max(1, value);
    }

    public int max() {
        return max;
    }

    public boolean take(int cost) {
        if (cost <= 0) {
            return true;
        }
        roll();
        if (spent > 0 && spent + cost > max) {
            return false;
        }
        spent += cost;
        return true;
    }

    public int left() {
        roll();
        return Math.max(0, max - spent);
    }

    private void roll() {
        long now = clock.getAsLong();
        if (now != tick) {
            tick = now;
            spent = 0;
        }
    }
}
