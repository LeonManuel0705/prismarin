package net.prismarin.shared.storage;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class SqlDocumentStore implements DocumentStore {

    private static final Pattern PREFILTERABLE = Pattern.compile("[A-Za-z0-9._:-]+");
    private static final String FIND = "SELECT body FROM prismarin_documents WHERE collection = ? AND id = ?";
    private static final String FIND_ALL = "SELECT body FROM prismarin_documents WHERE collection = ? ORDER BY id";
    private static final String ENTRIES = "SELECT id, body FROM prismarin_documents WHERE collection = ? ORDER BY id";
    private static final String FIND_CONTAINING = "SELECT body FROM prismarin_documents WHERE collection = ? "
            + "AND body LIKE ? ESCAPE '!' ORDER BY id";
    private static final String DELETE = "DELETE FROM prismarin_documents WHERE collection = ? AND id = ?";

    private final SqlDatabase database;
    private final String upsert;

    public SqlDocumentStore(SqlDatabase database) {
        this.database = database;
        SqlDialect dialect = database.dialect();
        this.upsert = dialect.upsert("prismarin_documents", "body", "collection", "id");
        String table = "CREATE TABLE IF NOT EXISTS prismarin_documents ("
                + "collection VARCHAR(64) NOT NULL, "
                + "id " + dialect.binaryType() + " NOT NULL, "
                + "body " + dialect.largeTextType() + " NOT NULL, "
                + "PRIMARY KEY (collection, id))" + dialect.tableOptions();
        database.execute(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(table);
            }
            return null;
        });
    }

    @Override
    public void save(String collection, String id, String json) {
        DocumentKeys.collection(collection);
        byte[] key = key(id);
        Objects.requireNonNull(json, "json");
        database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, collection);
                statement.setBytes(2, key);
                statement.setString(3, json);
                statement.setString(4, json);
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Optional<String> find(String collection, String id) {
        DocumentKeys.collection(collection);
        byte[] key = key(id);
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(FIND)) {
                statement.setString(1, collection);
                statement.setBytes(2, key);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(rows.getString(1)) : Optional.<String>empty();
                }
            }
        });
    }

    @Override
    public List<String> findAll(String collection) {
        return bodies(FIND_ALL, DocumentKeys.collection(collection), null);
    }

    @Override
    public Map<String, String> entries(String collection) {
        String name = DocumentKeys.collection(collection);
        return database.execute(connection -> {
            Map<String, String> entries = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(ENTRIES)) {
                statement.setString(1, name);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        entries.put(new String(rows.getBytes(1), StandardCharsets.UTF_8), rows.getString(2));
                    }
                }
            }
            return entries;
        });
    }

    @Override
    public List<String> findBy(String collection, String field, String value) {
        DocumentKeys.collection(collection);
        if (field == null || value == null) {
            return List.of();
        }
        List<String> candidates = PREFILTERABLE.matcher(value).matches()
                ? bodies(FIND_CONTAINING, collection, "%" + escapeLike(value) + "%")
                : bodies(FIND_ALL, collection, null);
        return candidates.stream().filter(json -> JsonFileDocumentStore.matches(json, field, value)).toList();
    }

    @Override
    public boolean delete(String collection, String id) {
        DocumentKeys.collection(collection);
        byte[] key = key(id);
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(DELETE)) {
                statement.setString(1, collection);
                statement.setBytes(2, key);
                return statement.executeUpdate() > 0;
            }
        });
    }

    @Override
    public void close() {
    }

    private List<String> bodies(String sql, String collection, String pattern) {
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, collection);
                if (pattern != null) {
                    statement.setString(2, pattern);
                }
                try (ResultSet rows = statement.executeQuery()) {
                    List<String> bodies = new ArrayList<>();
                    while (rows.next()) {
                        bodies.add(rows.getString(1));
                    }
                    return bodies;
                }
            }
        });
    }

    private static byte[] key(String id) {
        return DocumentKeys.id(id).getBytes(StandardCharsets.UTF_8);
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
