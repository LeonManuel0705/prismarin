package net.prismarin.shared.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteDocumentStoreTest extends DocumentStoreContract {

    @TempDir
    Path directory;

    private SqlDatabase database;
    private SqlDocumentStore store;

    @BeforeEach
    void setUp() {
        database = SqlDatabase.sqlite(directory.resolve("prismarin.db"));
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

    @Test
    void documentsSurviveReopeningTheDatabase() {
        store.save("names", "leon", "{\"name\":\"Leon\"}");
        database.close();

        database = SqlDatabase.sqlite(directory.resolve("prismarin.db"));
        store = new SqlDocumentStore(database);

        assertEquals(Optional.of("{\"name\":\"Leon\"}"), store.find("names", "leon"));
        assertEquals(List.of("{\"name\":\"Leon\"}"), store.findBy("names", "name", "Leon"));
    }

    @Test
    void idsUpToTheMaximumLengthAreStored() {
        String wide = "字".repeat(128);
        String ascii = "x".repeat(128);
        store.save("long", wide, "{\"v\":\"wide\"}");
        store.save("long", ascii, "{\"v\":\"ascii\"}");

        assertEquals(Optional.of("{\"v\":\"wide\"}"), store.find("long", wide));
        assertEquals(Optional.of("{\"v\":\"ascii\"}"), store.find("long", ascii));
    }
}
