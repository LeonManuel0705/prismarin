package net.prismarin.shared.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EffectEntityRegistryTest {

    @Test
    void markedEntitiesBelongToTheirEffectUntilReleased() {
        EffectEntityRegistry registry = new EffectEntityRegistry(10);
        UUID squid = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        assertTrue(registry.mark(squid, 1L));
        assertTrue(registry.marked(squid));
        assertFalse(registry.marked(stranger));
        assertFalse(registry.marked(null));
        assertTrue(registry.release(squid));
        assertFalse(registry.marked(squid));
        assertFalse(registry.release(squid));
    }

    @Test
    void theLimitStopsNewEntitiesButAMarkedOneStaysMarked() {
        EffectEntityRegistry registry = new EffectEntityRegistry(2);
        UUID first = UUID.randomUUID();

        assertTrue(registry.mark(first, 1L));
        assertTrue(registry.mark(UUID.randomUUID(), 1L));
        assertFalse(registry.room(1));
        assertFalse(registry.mark(UUID.randomUUID(), 2L));
        assertTrue(registry.mark(first, 1L));
        assertEquals(2, registry.size());
        registry.max(3);
        assertTrue(registry.room(1));
    }

    @Test
    void endingAnEffectReleasesAllOfItsEntities() {
        EffectEntityRegistry registry = new EffectEntityRegistry(10);
        UUID sheep = UUID.randomUUID();
        UUID lamb = UUID.randomUUID();
        UUID firework = UUID.randomUUID();
        registry.mark(sheep, 4L);
        registry.mark(lamb, 4L);
        registry.mark(firework, 5L);

        assertEquals(Set.of(sheep, lamb), Set.copyOf(registry.releaseRun(4L)));
        assertFalse(registry.marked(sheep));
        assertTrue(registry.marked(firework));
        registry.adopt(UUID.randomUUID(), 6L);
        assertEquals(2, registry.size());
        registry.clear();
        assertEquals(0, registry.size());
    }
}
