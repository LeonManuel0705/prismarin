package net.prismarin.shared.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EffectSchedulerTest {

    static final class Clock implements TickClock {

        Runnable task;
        int starts;
        int stops;

        @Override
        public Runnable start(Runnable everyTick) {
            task = everyTick;
            starts++;
            return () -> {
                stops++;
                task = null;
            };
        }

        boolean running() {
            return task != null;
        }

        void tick() {
            if (task != null) {
                task.run();
            }
        }
    }

    static final class Run implements EffectScheduler.Run {

        final String name;
        final int duration;
        final ParticleBudget budget;
        final int cost;
        final List<Integer> frames = new ArrayList<>();
        final List<Integer> drawn = new ArrayList<>();
        int ends;
        boolean fail;
        boolean stopAt5;

        Run(String name, int duration, ParticleBudget budget, int cost) {
            this.name = name;
            this.duration = duration;
            this.budget = budget;
            this.cost = cost;
        }

        @Override
        public int duration() {
            return duration;
        }

        @Override
        public boolean frame(int tick) {
            if (fail) {
                throw new IllegalStateException("broken");
            }
            frames.add(tick);
            if (budget == null || budget.take(cost)) {
                drawn.add(tick);
            }
            return !(stopAt5 && tick == 5);
        }

        @Override
        public void end() {
            ends++;
        }
    }

    @Test
    void aOneFrameEffectPlaysAtOnceWithoutATask() {
        Clock clock = new Clock();
        EffectScheduler scheduler = new EffectScheduler(clock, 8);
        Run burst = new Run("burst", 0, null, 0);

        assertTrue(scheduler.play(burst));

        assertEquals(List.of(0), burst.frames);
        assertEquals(1, burst.ends);
        assertEquals(0, clock.starts);
        assertFalse(scheduler.ticking());
        assertEquals(0, scheduler.running());
    }

    @Test
    void aRunningEffectGetsEveryFrameOfItsDurationAndThenStops() {
        Clock clock = new Clock();
        EffectScheduler scheduler = new EffectScheduler(clock, 8);
        Run show = new Run("show", 4, null, 0);

        scheduler.play(show);
        assertTrue(clock.running());
        assertEquals(1, scheduler.running());
        for (int tick = 0; tick < 10; tick++) {
            clock.tick();
        }

        assertEquals(List.of(0, 1, 2, 3, 4), show.frames);
        assertEquals(1, show.ends);
        assertEquals(1, clock.starts);
        assertEquals(1, clock.stops);
        assertFalse(scheduler.ticking());
    }

    @Test
    void anEffectCanEndEarlyAndAFailingOneIsStopped() {
        Clock clock = new Clock();
        EffectScheduler scheduler = new EffectScheduler(clock, 8);
        Run early = new Run("early", 50, null, 0);
        early.stopAt5 = true;
        Run broken = new Run("broken", 50, null, 0);

        scheduler.play(early);
        scheduler.play(broken);
        broken.fail = true;
        for (int tick = 0; tick < 10; tick++) {
            clock.tick();
        }

        assertEquals(List.of(0, 1, 2, 3, 4, 5), early.frames);
        assertEquals(1, early.ends);
        assertEquals(List.of(0), broken.frames);
        assertEquals(1, broken.ends);
        assertFalse(clock.running());
    }

    @Test
    void effectsShareTheParticleBudgetAndTakeTurns() {
        Clock clock = new Clock();
        long[] now = {0L};
        ParticleBudget budget = new ParticleBudget(250, () -> now[0]);
        EffectScheduler scheduler = new EffectScheduler(clock, 8);
        Run first = new Run("first", 20, budget, 100);
        Run second = new Run("second", 20, budget, 100);
        Run third = new Run("third", 20, budget, 100);

        scheduler.play(first);
        scheduler.play(second);
        scheduler.play(third);
        assertEquals(List.of(0), first.drawn);
        assertEquals(List.of(0), second.drawn);
        assertEquals(List.of(), third.drawn);
        for (int tick = 1; tick <= 6; tick++) {
            now[0] = tick;
            clock.tick();
        }

        assertEquals(7, first.frames.size());
        int drawn = first.drawn.size() + second.drawn.size() + third.drawn.size();
        assertEquals(2 + 6 * 2, drawn);
        assertTrue(third.drawn.size() >= 3, third.drawn.toString());
        assertTrue(first.drawn.size() >= 4 && second.drawn.size() >= 4);
    }

    @Test
    void tooManyEffectsAreRefusedAndCleanupEndsEverything() {
        Clock clock = new Clock();
        EffectScheduler scheduler = new EffectScheduler(clock, 2);
        Run one = new Run("one", 40, null, 0);
        Run two = new Run("two", 40, null, 0);
        Run three = new Run("three", 40, null, 0);

        assertTrue(scheduler.play(one));
        assertTrue(scheduler.play(two));
        assertFalse(scheduler.play(three));
        assertEquals(List.of(), three.frames);
        assertEquals(0, three.ends);

        assertEquals(1, scheduler.cancel(run -> run == one));
        assertEquals(1, one.ends);
        assertEquals(List.of(two), scheduler.runs());
        assertTrue(scheduler.ticking());

        scheduler.stop();
        assertEquals(1, two.ends);
        assertEquals(0, scheduler.running());
        assertFalse(clock.running());
        assertEquals(1, clock.stops);
        scheduler.stop();
        assertEquals(1, two.ends);
    }

    @Test
    void theBudgetResetsEveryTickAndAlwaysLetsTheFirstThrough() {
        long[] now = {7L};
        ParticleBudget budget = new ParticleBudget(100, () -> now[0]);

        assertTrue(budget.take(400));
        assertFalse(budget.take(1));
        assertEquals(0, budget.left());
        now[0] = 8L;
        assertEquals(100, budget.left());
        assertTrue(budget.take(60));
        assertTrue(budget.take(40));
        assertFalse(budget.take(1));
        assertTrue(budget.take(0));
        budget.max(200);
        assertTrue(budget.take(100));
    }
}
