package net.prismarin.shared.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

abstract class DocumentStoreContract {

    private final String collection = "test-" + Long.toHexString(ThreadLocalRandom.current().nextLong() >>> 1);
    private final String otherCollection = collection + "_other";

    protected abstract DocumentStore store();

    @Test
    void savedDocumentIsReturnedVerbatim() {
        String json = "{ \"b\": 1,\n  \"a\": \"x\" }";
        store().save(collection, "doc", json);

        assertEquals(Optional.of(json), store().find(collection, "doc"));
    }

    @Test
    void findOfUnknownIdIsEmpty() {
        assertTrue(store().find(collection, "missing").isEmpty());
    }

    @Test
    void saveReplacesExistingDocument() {
        store().save(collection, "doc", "{\"v\":1}");
        store().save(collection, "doc", "{\"v\":2}");

        assertEquals(Optional.of("{\"v\":2}"), store().find(collection, "doc"));
        assertEquals(List.of("{\"v\":2}"), store().findAll(collection));
    }

    @Test
    void findAllReturnsOneCollectionSortedById() {
        store().save(collection, "c", "{\"n\":\"c\"}");
        store().save(collection, "a", "{\"n\":\"a\"}");
        store().save(collection, "b", "{\"n\":\"b\"}");
        store().save(otherCollection, "a", "{\"n\":\"other\"}");

        assertEquals(List.of("{\"n\":\"a\"}", "{\"n\":\"b\"}", "{\"n\":\"c\"}"), store().findAll(collection));
        assertEquals(List.of("{\"n\":\"other\"}"), store().findAll(otherCollection));
    }

    @Test
    void findAllOfUnusedCollectionIsEmpty() {
        assertEquals(List.of(), store().findAll(collection));
    }

    @Test
    void findByMatchesTopLevelPrimitivesAsStrings() {
        String first = "{\"target\":\"abc\",\"count\":5,\"active\":true}";
        String prefix = "{\"target\":\"abcd\"}";
        String nested = "{\"nested\":{\"target\":\"abc\"}}";
        String array = "{\"target\":[\"abc\"]}";
        String upper = "{\"target\":\"ABC\"}";
        String second = "{\"other\":1,\"target\":\"abc\"}";
        store().save(collection, "1", first);
        store().save(collection, "2", prefix);
        store().save(collection, "3", nested);
        store().save(collection, "4", array);
        store().save(collection, "5", upper);
        store().save(collection, "6", second);
        store().save(otherCollection, "1", first);

        assertEquals(List.of(first, second), store().findBy(collection, "target", "abc"));
        assertEquals(List.of(first), store().findBy(collection, "count", "5"));
        assertEquals(List.of(first), store().findBy(collection, "active", "true"));
        assertEquals(List.of(upper), store().findBy(collection, "target", "ABC"));
        assertEquals(List.of(), store().findBy(collection, "target", "ab"));
        assertEquals(List.of(), store().findBy(collection, "missing", "abc"));
    }

    @Test
    void findByHandlesValuesWithSpecialCharacters() {
        String underscore = "{\"code\":\"a_c\"}";
        String plain = "{\"code\":\"abc\"}";
        String quoted = "{\"title\":\"say \\\"hi\\\" & <b>\"}";
        String escaped = "{\"title\":\"a\\u003db\"}";
        String umlaut = "{\"title\":\"Grüße 100%\"}";
        store().save(collection, "1", underscore);
        store().save(collection, "2", plain);
        store().save(collection, "3", quoted);
        store().save(collection, "4", escaped);
        store().save(collection, "5", umlaut);

        assertEquals(List.of(underscore), store().findBy(collection, "code", "a_c"));
        assertEquals(List.of(plain), store().findBy(collection, "code", "abc"));
        assertEquals(List.of(quoted), store().findBy(collection, "title", "say \"hi\" & <b>"));
        assertEquals(List.of(escaped), store().findBy(collection, "title", "a=b"));
        assertEquals(List.of(umlaut), store().findBy(collection, "title", "Grüße 100%"));
    }

    @Test
    void findByTreatsFieldNamesLiterally() {
        String json = "{\"a.b\":\"x\",\"a\":{\"b\":\"y\"},\"$v\":\"z\"}";
        store().save(collection, "doc", json);

        assertEquals(List.of(json), store().findBy(collection, "a.b", "x"));
        assertEquals(List.of(), store().findBy(collection, "a.b", "y"));
        assertEquals(List.of(json), store().findBy(collection, "$v", "z"));
    }

    @Test
    void deleteRemovesDocumentAndReportsWhetherItExisted() {
        store().save(collection, "doc", "{}");

        assertTrue(store().delete(collection, "doc"));
        assertFalse(store().delete(collection, "doc"));
        assertTrue(store().find(collection, "doc").isEmpty());
        assertEquals(List.of(), store().findAll(collection));
    }

    @Test
    void idsAreExactAndCaseSensitive() {
        store().save(collection, "Leon", "{\"v\":\"upper\"}");
        store().save(collection, "leon", "{\"v\":\"lower\"}");
        store().save(collection, "leon ", "{\"v\":\"space\"}");

        assertEquals(Optional.of("{\"v\":\"upper\"}"), store().find(collection, "Leon"));
        assertEquals(Optional.of("{\"v\":\"lower\"}"), store().find(collection, "leon"));
        assertEquals(Optional.of("{\"v\":\"space\"}"), store().find(collection, "leon "));
        assertEquals(3, store().findAll(collection).size());
    }

    @Test
    void unicodeAndPunctuationIdsAreSupported() {
        String id = "spieler/ä ß 字:1?*";
        store().save(collection, id, "{\"v\":1}");

        assertEquals(Optional.of("{\"v\":1}"), store().find(collection, id));
        assertTrue(store().delete(collection, id));
    }

    @Test
    void collectionsAreIsolated() {
        store().save(collection, "doc", "{\"v\":1}");
        store().save(otherCollection, "doc", "{\"v\":2}");

        assertTrue(store().delete(otherCollection, "doc"));
        assertEquals(Optional.of("{\"v\":1}"), store().find(collection, "doc"));
    }

    @Test
    void invalidCollectionNamesAreRejected() {
        for (String invalid : new String[] {"Upper", "with space", "../escape", "", "x".repeat(65), null}) {
            assertThrows(IllegalArgumentException.class, () -> store().save(invalid, "doc", "{}"));
            assertThrows(IllegalArgumentException.class, () -> store().find(invalid, "doc"));
            assertThrows(IllegalArgumentException.class, () -> store().findAll(invalid));
            assertThrows(IllegalArgumentException.class, () -> store().findBy(invalid, "a", "b"));
            assertThrows(IllegalArgumentException.class, () -> store().delete(invalid, "doc"));
        }
    }

    @Test
    void invalidIdsAreRejected() {
        for (String invalid : new String[] {"", "x".repeat(129), null}) {
            assertThrows(IllegalArgumentException.class, () -> store().save(collection, invalid, "{}"));
            assertThrows(IllegalArgumentException.class, () -> store().find(collection, invalid));
            assertThrows(IllegalArgumentException.class, () -> store().delete(collection, invalid));
        }
    }

    @Test
    void collectionsListEveryCollectionWithDocuments() {
        store().save(collection, "a", "{}");
        store().save(otherCollection, "b", "{}");

        List<String> names = store().collections();
        assertTrue(names.contains(collection));
        assertTrue(names.contains(otherCollection));
        assertEquals(names.stream().sorted().toList(), names);
    }

    @Test
    void entriesReturnIdsWithBodies() {
        store().save(collection, "b", "{\"v\":2}");
        store().save(collection, "a", "{\"v\":1}");
        store().save(otherCollection, "c", "{\"v\":3}");

        assertEquals(Map.of("a", "{\"v\":1}", "b", "{\"v\":2}"), store().entries(collection));
        assertTrue(store().entries(collection + "_none").isEmpty());
    }

    @Test
    void updateCreatesAMissingDocument() {
        Optional<String> result = store().update(collection, "counter",
                current -> current == null ? "{\"n\":1}" : null);

        assertEquals(Optional.of("{\"n\":1}"), result);
        assertEquals(Optional.of("{\"n\":1}"), store().find(collection, "counter"));
    }

    @Test
    void updateSeesTheStoredDocument() {
        store().save(collection, "counter", "{\"n\":4}");

        Optional<String> result = store().update(collection, "counter",
                current -> "{\"n\":" + (count(current) + 1) + "}");

        assertEquals(Optional.of("{\"n\":5}"), result);
        assertEquals(Optional.of("{\"n\":5}"), store().find(collection, "counter"));
    }

    @Test
    void updateReturningNullWritesNothing() {
        store().save(collection, "kept", "{\"n\":2}");

        assertEquals(Optional.of("{\"n\":2}"), store().update(collection, "kept", current -> null));
        assertEquals(Optional.empty(), store().update(collection, "absent", current -> null));
        assertTrue(store().find(collection, "absent").isEmpty());
        assertEquals(Optional.of("{\"n\":2}"), store().find(collection, "kept"));
    }

    @Test
    void concurrentUpdatesNeverLoseAChange() {
        int threads = 6;
        int rounds = 20;
        AtomicInteger decided = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CompletableFuture<?>[] work = new CompletableFuture<?>[threads];
            for (int thread = 0; thread < threads; thread++) {
                work[thread] = CompletableFuture.runAsync(() -> {
                    for (int round = 0; round < rounds; round++) {
                        store().update(collection, "shared", current -> "{\"n\":" + (count(current) + 1) + "}");
                        decided.incrementAndGet();
                    }
                }, executor);
            }
            CompletableFuture.allOf(work).join();
        } finally {
            executor.shutdown();
        }

        assertEquals(threads * rounds, decided.get());
        assertEquals(Optional.of("{\"n\":" + threads * rounds + "}"), store().find(collection, "shared"));
    }

    @Test
    void onlyOneOfManyClaimsWins() {
        int threads = 6;
        AtomicInteger winners = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CompletableFuture<?>[] work = new CompletableFuture<?>[threads];
            for (int thread = 0; thread < threads; thread++) {
                String claimer = "{\"by\":\"" + thread + "\"}";
                work[thread] = CompletableFuture.runAsync(() -> {
                    boolean[] won = new boolean[1];
                    store().update(collection, "claim", current -> {
                        won[0] = current == null;
                        return current == null ? claimer : null;
                    });
                    if (won[0]) {
                        winners.incrementAndGet();
                    }
                }, executor);
            }
            CompletableFuture.allOf(work).join();
        } finally {
            executor.shutdown();
        }

        assertEquals(1, winners.get());
    }

    private static int count(String json) {
        if (json == null) {
            return 0;
        }
        return Integer.parseInt(json.replaceAll("[^0-9]", ""));
    }
}
