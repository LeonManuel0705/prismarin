package net.prismarin.shared.storage;

import java.util.Map;
import net.prismarin.shared.config.SqlSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

@Tag("integration")
@Testcontainers
class MariaDbStorageIT {

    @Container
    private static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:11.4")
            .withUrlParam("sslMode", "disable");

    private static SqlDatabase open() {
        return SqlDatabase.mysql(new SqlSettings(MARIADB.getHost(), MARIADB.getMappedPort(3306),
                MARIADB.getDatabaseName(), MARIADB.getUsername(), MARIADB.getPassword(), 4,
                Map.of("sslMode", "disable"), "unused.db"));
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
