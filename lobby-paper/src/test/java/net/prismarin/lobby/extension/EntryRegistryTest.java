package net.prismarin.lobby.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.prismarin.api.event.Subscription;
import org.junit.jupiter.api.Test;

class EntryRegistryTest {

    private record Entry(String id, int order, int slot) {
    }

    private final EntryRegistry<Entry> registry = new EntryRegistry<>("test entry", Entry::id, Entry::order,
            entry -> HotbarSlots.problem(entry.slot()));

    @Test
    void entriesAreSortedByOrderThenRegistration() {
        registry.register(new Entry("stats", 5, 0));
        registry.register(new Entry("shop", 0, 1));
        registry.register(new Entry("cosmetics", 5, 2));

        assertEquals(List.of("shop", "stats", "cosmetics"), registry.all().stream().map(Entry::id).toList());
    }

    @Test
    void idsMustBeValidAndUnique() {
        registry.register(new Entry("shop", 0, 0));

        assertThrows(IllegalArgumentException.class, () -> registry.register(new Entry("shop", 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new Entry("Shop", 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new Entry("my shop", 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new Entry(null, 0, 0)));
        assertThrows(NullPointerException.class, () -> registry.register(null));
    }

    @Test
    void problemsRejectTheEntry() {
        assertThrows(IllegalArgumentException.class, () -> registry.register(new Entry("far", 0, 9)));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new Entry("negative", 0, -1)));
        assertTrue(registry.isEmpty());
    }

    @Test
    void unsubscribeFreesTheId() {
        Subscription shop = registry.register(new Entry("shop", 0, 0));

        shop.unsubscribe();
        registry.register(new Entry("shop", 0, 4));

        assertEquals(Optional.of(new Entry("shop", 0, 4)), registry.get("shop"));
    }

    @Test
    void getFindsById() {
        registry.register(new Entry("shop", 0, 0));

        assertTrue(registry.get("shop").isPresent());
        assertFalse(registry.get("other").isPresent());
        assertFalse(registry.get(null).isPresent());
    }

    @Test
    void callSwallowsFailures() {
        Entry entry = new Entry("broken", 0, 0);

        assertEquals(Optional.empty(), registry.call(entry, () -> {
            throw new IllegalStateException("boom");
        }));
        assertEquals(Optional.of(3), registry.call(entry, () -> 3));
        assertEquals(Optional.empty(), registry.call(entry, () -> null));
    }

    @Test
    void hotbarSlotsCoverTheNineSlots() {
        assertTrue(HotbarSlots.valid(0));
        assertTrue(HotbarSlots.valid(8));
        assertFalse(HotbarSlots.valid(9));
        assertFalse(HotbarSlots.valid(-1));
        assertNull(HotbarSlots.problem(4));
    }
}
