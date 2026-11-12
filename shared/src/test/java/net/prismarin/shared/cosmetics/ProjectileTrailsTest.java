package net.prismarin.shared.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticParticles;
import net.prismarin.api.cosmetics.CosmeticSlot;
import org.junit.jupiter.api.Test;

class ProjectileTrailsTest {

    private static final Cosmetic SPARKS = Cosmetic.particles("spark-arrows", CosmeticSlot.PROJECTILE_TRAIL, null,
            CosmeticParticles.of("point", "electric_spark"));
    private static final UUID ARCHER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    static final class Arrow {

        final String name;
        final String chunk;
        boolean flying = true;

        Arrow(String name, String chunk) {
            this.name = name;
            this.chunk = chunk;
        }
    }

    static final class Driver implements ProjectileTrails.Driver<Arrow> {

        final List<String> painted = new ArrayList<>();
        ParticleBudget budget;
        int cost = 1;

        @Override
        public boolean flying(Arrow projectile) {
            return projectile.flying;
        }

        @Override
        public boolean paint(Arrow projectile, UUID owner, Cosmetic cosmetic, long age) {
            if (budget != null && !budget.take(cost)) {
                return false;
            }
            painted.add(projectile.name + "@" + age);
            return true;
        }
    }

    private final EffectSchedulerTest.Clock clock = new EffectSchedulerTest.Clock();
    private final Driver driver = new Driver();

    @Test
    void aTrailStartsTheTaskFollowsTheArrowAndStopsWhenItLands() {
        ProjectileTrails<Arrow> trails = new ProjectileTrails<>(clock, driver, 1, 100, 10);
        Arrow arrow = new Arrow("a", "0,0");
        UUID id = UUID.randomUUID();

        assertFalse(clock.running());
        assertTrue(trails.track(id, arrow, ARCHER, SPARKS));
        assertFalse(trails.track(id, arrow, ARCHER, SPARKS));
        assertTrue(clock.running());
        assertTrue(trails.tracking(id));
        clock.tick();
        clock.tick();
        clock.tick();
        assertEquals(List.of("a@0", "a@1", "a@2"), driver.painted);

        trails.land(id);
        assertFalse(trails.tracking(id));
        assertFalse(clock.running());
        assertEquals(1, clock.stops);
    }

    @Test
    void anArrowThatStopsFlyingOrFliesTooLongIsDropped() {
        ProjectileTrails<Arrow> trails = new ProjectileTrails<>(clock, driver, 2, 5, 10);
        Arrow stuck = new Arrow("stuck", "0,0");
        Arrow far = new Arrow("long", "0,0");
        trails.track(UUID.randomUUID(), stuck, ARCHER, SPARKS);
        trails.track(UUID.randomUUID(), far, ARCHER, SPARKS);

        clock.tick();
        stuck.flying = false;
        for (int tick = 0; tick < 10; tick++) {
            clock.tick();
        }

        assertEquals(List.of("stuck@0", "long@0", "long@2", "long@4"), driver.painted);
        assertEquals(0, trails.tracked());
        assertFalse(clock.running());
    }

    @Test
    void theBudgetIsSharedAndARefusedTrailGoesFirstNextTick() {
        long[] now = {0L};
        driver.budget = new ParticleBudget(2, () -> now[0]);
        ProjectileTrails<Arrow> trails = new ProjectileTrails<>(clock, driver, 1, 100, 10);
        trails.track(UUID.randomUUID(), new Arrow("a", "0,0"), ARCHER, SPARKS);
        trails.track(UUID.randomUUID(), new Arrow("b", "0,0"), ARCHER, SPARKS);
        trails.track(UUID.randomUUID(), new Arrow("c", "0,0"), ARCHER, SPARKS);

        clock.tick();
        now[0] = 1L;
        clock.tick();
        now[0] = 2L;
        clock.tick();

        assertEquals(List.of("a@0", "b@0", "c@1", "a@1", "b@2", "c@2"), driver.painted);
    }

    @Test
    void quittingPlayersUnloadedChunksAndTheLimitAreRespected() {
        ProjectileTrails<Arrow> trails = new ProjectileTrails<>(clock, driver, 1, 100, 3);
        trails.track(UUID.randomUUID(), new Arrow("a", "0,0"), ARCHER, SPARKS);
        trails.track(UUID.randomUUID(), new Arrow("b", "5,5"), ARCHER, SPARKS);
        trails.track(UUID.randomUUID(), new Arrow("c", "5,5"), OTHER, SPARKS);
        assertFalse(trails.track(UUID.randomUUID(), new Arrow("d", "0,0"), OTHER, SPARKS));

        assertEquals(1, trails.forget(arrow -> arrow.chunk.equals("0,0")));
        assertEquals(1, trails.forgetOwner(ARCHER));
        assertEquals(1, trails.tracked());
        assertTrue(clock.running());
        trails.stop();
        assertEquals(0, trails.tracked());
        assertFalse(clock.running());
        assertTrue(trails.track(UUID.randomUUID(), new Arrow("e", "0,0"), OTHER, SPARKS));
        assertEquals(2, clock.starts);
    }

    @Test
    void aChangedIntervalAndLimitApplyRightAway() {
        ProjectileTrails<Arrow> trails = new ProjectileTrails<>(clock, driver, 1, 100, 1);
        trails.configure(3, 100, 2);
        trails.track(UUID.randomUUID(), new Arrow("a", "0,0"), ARCHER, SPARKS);
        trails.track(UUID.randomUUID(), new Arrow("b", "0,0"), ARCHER, SPARKS);

        for (int tick = 0; tick < 7; tick++) {
            clock.tick();
        }

        assertEquals(List.of("a@0", "a@3", "a@6", "b@0", "b@3", "b@6"), driver.painted.stream().sorted().toList());
    }
}
