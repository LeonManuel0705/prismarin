package net.prismarin.shared.cosmetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EffectScheduler {

    private static final Logger LOGGER = Logger.getLogger(EffectScheduler.class.getName());

    public interface Run {

        int duration();

        boolean frame(int tick);

        void end();
    }

    private static final class Entry {

        private final Run run;
        private int tick;

        private Entry(Run run) {
            this.run = run;
        }
    }

    private final TickClock clock;
    private final List<Entry> runs = new ArrayList<>();
    private int maxRunning;
    private Runnable stopper;
    private int offset;

    public EffectScheduler(TickClock clock, int maxRunning) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxRunning = Math.max(1, maxRunning);
    }

    public void maxRunning(int value) {
        maxRunning = Math.max(1, value);
    }

    public boolean play(Run run) {
        Objects.requireNonNull(run, "run");
        if (runs.size() >= maxRunning) {
            return false;
        }
        Entry entry = new Entry(run);
        if (!step(entry)) {
            finish(entry);
            return true;
        }
        runs.add(entry);
        schedule();
        return true;
    }

    public int cancel(Predicate<Run> which) {
        int cancelled = 0;
        for (Entry entry : List.copyOf(runs)) {
            if (runs.contains(entry) && which.test(entry.run)) {
                runs.remove(entry);
                finish(entry);
                cancelled++;
            }
        }
        stopWhenIdle();
        return cancelled;
    }

    public void stop() {
        cancel(run -> true);
        stopClock();
    }

    public int running() {
        return runs.size();
    }

    public boolean ticking() {
        return stopper != null;
    }

    public List<Run> runs() {
        return runs.stream().map(entry -> entry.run).toList();
    }

    public void tick() {
        List<Entry> order = List.copyOf(runs);
        int size = order.size();
        if (size > 0) {
            int start = Math.floorMod(offset, size);
            for (int step = 0; step < size; step++) {
                Entry entry = order.get((start + step) % size);
                if (runs.contains(entry) && !step(entry)) {
                    runs.remove(entry);
                    finish(entry);
                }
            }
            offset = start + 1;
        }
        stopWhenIdle();
    }

    private boolean step(Entry entry) {
        boolean alive;
        try {
            alive = entry.run.frame(entry.tick);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "A cosmetic effect failed and was stopped", e);
            alive = false;
        }
        entry.tick++;
        return alive && entry.tick <= entry.run.duration();
    }

    private void finish(Entry entry) {
        try {
            entry.run.end();
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "A cosmetic effect could not clean up", e);
        }
    }

    private void schedule() {
        if (stopper == null && !runs.isEmpty()) {
            stopper = clock.start(this::tick);
        }
    }

    private void stopWhenIdle() {
        if (runs.isEmpty()) {
            stopClock();
        }
    }

    private void stopClock() {
        Runnable current = stopper;
        stopper = null;
        if (current != null) {
            current.run();
        }
    }
}
