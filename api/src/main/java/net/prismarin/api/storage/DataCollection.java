package net.prismarin.api.storage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface DataCollection {

    String namespace();

    String name();

    CompletableFuture<Void> save(String id, String json);

    CompletableFuture<Optional<String>> find(String id);

    CompletableFuture<Boolean> delete(String id);

    CompletableFuture<Map<String, String>> list();

    CompletableFuture<List<String>> findBy(String field, String value);

    default CompletableFuture<List<String>> ids() {
        return list().thenApply(entries -> List.copyOf(entries.keySet()));
    }

    default CompletableFuture<Boolean> exists(String id) {
        return find(id).thenApply(Optional::isPresent);
    }
}
