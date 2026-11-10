package net.prismarin.shared.config;

import java.util.Map;
import java.util.Objects;

public record SqlSettings(String host, int port, String database, String username, String password, int poolSize,
                          Map<String, String> properties, String sqliteFile) {

    public static final SqlSettings DEFAULT = new SqlSettings("localhost", 3306, "prismarin", "prismarin", "", 8,
            Map.of(), "prismarin.db");

    public SqlSettings {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(sqliteFile, "sqliteFile");
        properties = Map.copyOf(properties);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("storage.mysql.port must be between 1 and 65535");
        }
        if (poolSize < 1) {
            throw new IllegalArgumentException("storage.mysql.pool-size must be at least 1");
        }
        if (sqliteFile.isBlank()) {
            throw new IllegalArgumentException("storage.sqlite.file must not be empty");
        }
    }
}
