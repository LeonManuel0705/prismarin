package net.prismarin.api.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class TypedCollectionTest {

    record Stats(String name, int wins, List<String> kits) {
    }

    @Test
    void recordsSurviveTheRoundTrip() {
        MemoryCollection raw = new MemoryCollection();
        TypedCollection<Stats> stats = TypedCollection.of(raw, Stats.class);
        Stats leon = new Stats("Leon", 7, List.of("archer", "tank"));

        stats.save("leon", leon).join();

        assertEquals(Optional.of(leon), stats.find("leon").join());
        assertEquals(7, JsonParser.parseString(raw.documents.get("leon")).getAsJsonObject().get("wins").getAsInt());
        assertEquals(Optional.empty(), stats.find("tim").join());
    }

    @Test
    void listAndFindByDecodeEveryDocument() {
        MemoryCollection raw = new MemoryCollection();
        TypedCollection<Stats> stats = TypedCollection.of(raw, Stats.class);
        stats.save("b", new Stats("Tim", 3, List.of())).join();
        stats.save("a", new Stats("Leon", 7, List.of())).join();

        Map<String, Stats> all = stats.list().join();
        assertEquals(List.of("a", "b"), List.copyOf(all.keySet()));
        assertEquals("Tim", all.get("b").name());
        assertEquals(List.of(new Stats("Leon", 7, List.of())), stats.findBy("name", "Leon").join());
        assertEquals(List.of("a", "b"), stats.ids().join());
        assertTrue(stats.delete("a").join());
    }

    @Test
    void emptyDocumentsFailInsteadOfReturningNull() {
        MemoryCollection raw = new MemoryCollection();
        raw.save("broken", "null").join();
        TypedCollection<Stats> stats = TypedCollection.of(raw, Stats.class);

        CompletionException error = assertThrows(CompletionException.class, () -> stats.find("broken").join());
        assertTrue(error.getCause() instanceof IllegalStateException);
    }

    @Test
    void nullValuesAreRejected() {
        TypedCollection<Stats> stats = TypedCollection.of(new MemoryCollection(), Stats.class);
        assertThrows(NullPointerException.class, () -> stats.save("x", null));
    }

    static final class MemoryCollection implements DataCollection {

        final Map<String, String> documents = new TreeMap<>();

        @Override
        public String namespace() {
            return "test";
        }

        @Override
        public String name() {
            return "stats";
        }

        @Override
        public CompletableFuture<Void> save(String id, String json) {
            documents.put(id, json);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Optional<String>> find(String id) {
            return CompletableFuture.completedFuture(Optional.ofNullable(documents.get(id)));
        }

        @Override
        public CompletableFuture<Boolean> delete(String id) {
            return CompletableFuture.completedFuture(documents.remove(id) != null);
        }

        @Override
        public CompletableFuture<Map<String, String>> list() {
            return CompletableFuture.completedFuture(new TreeMap<>(documents));
        }

        @Override
        public CompletableFuture<List<String>> findBy(String field, String value) {
            return CompletableFuture.completedFuture(documents.values().stream()
                    .filter(json -> {
                        var element = JsonParser.parseString(json).getAsJsonObject().get(field);
                        return element != null && element.getAsString().equals(value);
                    })
                    .toList());
        }
    }
}
