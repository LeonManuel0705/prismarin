package net.prismarin.shared.storage;

import java.util.Map;
import net.prismarin.shared.config.SqlSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Tag("integration")
@Testcontainers
class MySqlStorageIT {

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    private static SqlDatabase open() {
        return SqlDatabase.mysql(new SqlSettings(MYSQL.getHost(), MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT),
                MYSQL.getDatabaseName(), MYSQL.getUsername(), MYSQL.getPassword(), 4,
                Map.of("sslMode", "trust"), "unused.db"));
    }

    @Nested
    class Profiles extends ProfileRepositoryContract {

        private SqlDatabase database;
        private SqlProfileRepository repository;

        @BeforeEach
        void setUp() {
            database = open();
            repository = new SqlProfileRepository(database);
        }

        @AfterEach
        void tearDown() {
            database.close();
        }

        @Override
        protected ProfileRepository repository() {
            return repository;
        }
    }

    @Nested
    class Documents extends DocumentStoreContract {

        private SqlDatabase database;
        private SqlDocumentStore store;

        @BeforeEach
        void setUp() {
            database = open();
            store = new SqlDocumentStore(database);
        }

        @AfterEach
        void tearDown() {
            database.close();
        }

        @Override
        protected DocumentStore store() {
            return store;
        }
    }
}
