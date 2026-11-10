package net.prismarin.shared.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonFileDocumentStoreTest extends DocumentStoreContract {

    @TempDir
    Path directory;

    private JsonFileDocumentStore store;

    @BeforeEach
    void setUp() {
        store = new JsonFileDocumentStore(directory);
    }

    @Override
    protected DocumentStore store() {
        return store;
    }

    @Test
    void documentsAreFilesNamedAfterTheHexEncodedId() throws IOException {
        store.save("names", "Leon", "{\"name\":\"Leon\"}");

        Path file = directory.resolve("documents").resolve("names")
                .resolve(HexFormat.of().formatHex("Leon".getBytes(StandardCharsets.UTF_8)) + ".json");
        assertTrue(Files.exists(file));
        assertEquals("{\"name\":\"Leon\"}", Files.readString(file));
    }

    @Test
    void foreignFilesInACollectionFolderAreIgnored() throws IOException {
        store.save("names", "leon", "{}");
        Files.writeString(directory.resolve("documents").resolve("names").resolve("notes.txt"), "ignored");

        assertEquals(List.of("{}"), store.findAll("names"));
    }

    @Test
    void longIdsStayBelowTheFileNameLimit() {
        JsonFileDocumentStore store = new JsonFileDocumentStore(directory);
        String id = "ä".repeat(128);
        store.save("names", id, "{\"a\":1}");
        assertEquals(Optional.of("{\"a\":1}"), store.find("names", id));
    }
}
