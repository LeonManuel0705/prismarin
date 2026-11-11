package net.prismarin.shared.storage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.prismarin.api.storage.DataCollection;
import net.prismarin.api.storage.DataNamespace;
import net.prismarin.api.storage.DataStorage;
import net.prismarin.api.storage.StorageNames;

public final class PluginDataStorage implements DataStorage {

    private final DocumentStore documents;
    private final Executor executor;

    public PluginDataStorage(DocumentStore documents, Executor executor) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public DataNamespace namespace(String name) {
        return new Namespace(StorageNames.namespace(name));
    }

    private final class Namespace implements DataNamespace {

        private final String name;

        private Namespace(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public DataCollection collection(String collection) {
            String local = StorageNames.collection(collection);
            return new Collection(name, local, PluginCollections.physical(name, local));
        }
    }

    private final class Collection implements DataCollection {

        private final String namespace;
        private final String name;
        private final String physical;

        private Collection(String namespace, String name, String physical) {
            this.namespace = namespace;
            this.name = name;
            this.physical = physical;
        }

        @Override
        public String namespace() {
            return namespace;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public CompletableFuture<Void> save(String id, String json) {
            String key = StorageNames.id(id);
            Objects.requireNonNull(json, "json");
            return async(() -> {
                documents.save(physical, key, json);
                return null;
            });
        }

        @Override
        public CompletableFuture<Optional<String>> find(String id) {
            String key = StorageNames.id(id);
            return async(() -> documents.find(physical, key));
        }

        @Override
        public CompletableFuture<Boolean> delete(String id) {
            String key = StorageNames.id(id);
            return async(() -> documents.delete(physical, key));
        }

        @Override
        public CompletableFuture<Map<String, String>> list() {
            return async(() -> Collections.unmodifiableMap(new LinkedHashMap<>(documents.entries(physical))));
        }

        @Override
        public CompletableFuture<List<String>> findBy(String field, String value) {
            if (field == null || field.isEmpty() || value == null) {
                throw new IllegalArgumentException("findBy needs a field and a value");
            }
            return async(() -> List.copyOf(documents.findBy(physical, field, value)));
        }

        private <T> CompletableFuture<T> async(Supplier<T> task) {
            return CompletableFuture.supplyAsync(task, executor);
        }
    }
}
