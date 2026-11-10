package net.prismarin.shared.storage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DocumentStore extends AutoCloseable {

    void save(String collection, String id, String json);

    Optional<String> find(String collection, String id);

    List<String> findAll(String collection);

    Map<String, String> entries(String collection);

    List<String> findBy(String collection, String field, String value);

    boolean delete(String collection, String id);

    @Override
    void close();
}
