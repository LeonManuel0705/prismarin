package net.prismarin.shared.storage;

enum SqlDialect {

    MYSQL(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "VARBINARY(512)", "MEDIUMTEXT", " FOR UPDATE"),
    SQLITE("", "BLOB", "TEXT", "");

    private final String tableOptions;
    private final String binaryType;
    private final String largeTextType;
    private final String lockClause;

    SqlDialect(String tableOptions, String binaryType, String largeTextType, String lockClause) {
        this.tableOptions = tableOptions;
        this.binaryType = binaryType;
        this.largeTextType = largeTextType;
        this.lockClause = lockClause;
    }

    String tableOptions() {
        return tableOptions;
    }

    String binaryType() {
        return binaryType;
    }

    String largeTextType() {
        return largeTextType;
    }

    String lockClause() {
        return lockClause;
    }

    String upsert(String table, String valueColumn, String... keyColumns) {
        String keys = String.join(", ", keyColumns);
        String insert = "INSERT INTO " + table + " (" + keys + ", " + valueColumn + ") VALUES ("
                + "?, ".repeat(keyColumns.length) + "?)";
        return switch (this) {
            case MYSQL -> insert + " ON DUPLICATE KEY UPDATE " + valueColumn + " = ?";
            case SQLITE -> insert + " ON CONFLICT (" + keys + ") DO UPDATE SET " + valueColumn + " = ?";
        };
    }
}
