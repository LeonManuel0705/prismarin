package net.prismarin.api.storage;

import com.google.gson.Gson;

public interface DataNamespace {

    String name();

    DataCollection collection(String name);

    default <T> TypedCollection<T> collection(String name, Class<T> type) {
        return TypedCollection.of(collection(name), type);
    }

    default <T> TypedCollection<T> collection(String name, Class<T> type, Gson gson) {
        return TypedCollection.of(collection(name), type, gson);
    }
}
