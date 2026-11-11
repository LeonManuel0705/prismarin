package net.prismarin.core.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.prismarin.api.event.Subscription;
import org.junit.jupiter.api.Test;

class PriorityRegistryTest {

    private final PriorityRegistry<String> registry = new PriorityRegistry<>("test entry");

    @Test
    void highestPriorityComesFirst() {
        registry.register("lobby", 0);
        registry.register("bedwars", 100);
        registry.register("fallback", -10);

        assertEquals(List.of("bedwars", "lobby", "fallback"), registry.values());
    }

    @Test
    void equalPrioritiesKeepRegistrationOrder() {
        registry.register("first", 5);
        registry.register("second", 5);
        registry.register("third", 5);

        assertEquals(List.of("first", "second", "third"), registry.values());
    }

    @Test
    void unsubscribeRemovesOnlyThatEntry() {
        Subscription first = registry.register("same", 1);
        registry.register("same", 1);

        first.unsubscribe();
        first.unsubscribe();

        assertEquals(List.of("same"), registry.values());
        assertEquals(1, registry.size());
    }

    @Test
    void firstSkipsEntriesThatDoNotApply() {
        registry.register("bedwars", 100);
        registry.register("lobby", 0);

        assertEquals(Optional.of("lobby"), registry.first(value -> value.startsWith("l")));
        assertEquals(Optional.empty(), registry.first(value -> false));
    }

    @Test
    void firstTreatsAThrowingFilterAsNotApplying() {
        registry.register("broken", 100);
        registry.register("lobby", 0);

        assertEquals(Optional.of("lobby"), registry.first(value -> {
            if (value.equals("broken")) {
                throw new IllegalStateException("boom");
            }
            return true;
        }));
    }

    @Test
    void collectSkipsNullsAndFailures() {
        registry.register("a", 3);
        registry.register("b", 2);
        registry.register("c", 1);

        List<String> results = registry.collect(value -> switch (value) {
            case "a" -> "A";
            case "b" -> null;
            default -> throw new IllegalArgumentException(value);
        });

        assertEquals(List.of("A"), results);
    }

    @Test
    void collectAllFlattensInPriorityOrder() {
        registry.register("low", 0);
        registry.register("high", 9);

        assertEquals(List.of("high-1", "high-2", "low-1"), registry.collectAll(value -> value.equals("high")
                ? List.of("high-1", "high-2") : java.util.Arrays.asList("low-1", null)));
    }

    @Test
    void emptyRegistryAnswersQuickly() {
        assertTrue(registry.isEmpty());
        assertTrue(registry.collect(value -> value).isEmpty());
        assertTrue(registry.first(value -> true).isEmpty());
    }

    @Test
    void nullEntriesAreRejected() {
        assertThrows(NullPointerException.class, () -> registry.register(null, 0));
    }
}
