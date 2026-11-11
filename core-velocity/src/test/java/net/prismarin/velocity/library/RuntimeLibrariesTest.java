package net.prismarin.velocity.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

class RuntimeLibrariesTest {

    @TempDir
    Path folder;

    private static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    @Test
    void theIndexIsParsedAndFilteredByStorageMode() throws IOException {
        String text = "MONGO org.mongodb:bson:5.2.1 AA\nSQLITE org.xerial:sqlite-jdbc:3.54.1.0 BB\nbroken line\n";
        List<RuntimeLibraries.Library> index = RuntimeLibraries.parse(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals(2, index.size());
        RuntimeLibraries libraries = new RuntimeLibraries(index, folder, "https://repo.example/m2", (s, t) -> { },
                path -> { }, NOPLogger.NOP_LOGGER);
        assertEquals(List.of("sqlite-jdbc-3.54.1.0.jar"),
                libraries.needed(List.of("sqlite")).stream().map(RuntimeLibraries.Library::fileName).toList());
        assertEquals("org/mongodb/bson/5.2.1/bson-5.2.1.jar", index.getFirst().path());
        assertTrue(libraries.needed(List.of("MYSQL", "LOCAL")).isEmpty());
    }

    @Test
    void aDownloadedFileIsCheckedAndOnlyAddedOnce() throws Exception {
        byte[] jar = "jar bytes".getBytes(StandardCharsets.UTF_8);
        RuntimeLibraries.Library library = new RuntimeLibraries.Library("SQLITE", "org.xerial", "sqlite-jdbc", "1", sha(jar));
        List<URI> downloads = new ArrayList<>();
        List<Path> added = new ArrayList<>();
        RuntimeLibraries libraries = new RuntimeLibraries(List.of(library), folder, "https://repo.example/m2",
                (source, target) -> {
                    downloads.add(source);
                    Files.write(target, jar);
                }, added::add, NOPLogger.NOP_LOGGER);
        libraries.ensure(List.of("SQLITE"));
        libraries.ensure(List.of("SQLITE"));
        assertEquals(List.of(URI.create("https://repo.example/m2/org/xerial/sqlite-jdbc/1/sqlite-jdbc-1.jar")), downloads);
        assertEquals(List.of(folder.resolve("sqlite-jdbc-1.jar")), added);
    }

    @Test
    void aFilePlacedByHandIsUsedWithoutDownloading() throws Exception {
        byte[] jar = "offline copy".getBytes(StandardCharsets.UTF_8);
        Files.write(folder.resolve("bson-2.jar"), jar);
        List<URI> downloads = new ArrayList<>();
        RuntimeLibraries libraries = new RuntimeLibraries(
                List.of(new RuntimeLibraries.Library("MONGO", "org.mongodb", "bson", "2", sha(jar))), folder,
                "https://repo.example/", (source, target) -> downloads.add(source), path -> { }, NOPLogger.NOP_LOGGER);
        libraries.ensure(List.of("MONGO"));
        assertTrue(downloads.isEmpty());
    }

    @Test
    void aWrongChecksumIsRefusedAndLeavesNothingBehind() throws Exception {
        RuntimeLibraries.Library library = new RuntimeLibraries.Library("SQLITE", "org.xerial", "sqlite-jdbc", "1",
                sha("expected".getBytes(StandardCharsets.UTF_8)));
        List<Path> added = new ArrayList<>();
        RuntimeLibraries libraries = new RuntimeLibraries(List.of(library), folder, "https://repo.example/",
                (source, target) -> Files.writeString(target, "tampered"), added::add, NOPLogger.NOP_LOGGER);
        assertThrows(IllegalStateException.class, () -> libraries.ensure(List.of("SQLITE")));
        assertTrue(added.isEmpty());
        try (var files = Files.list(folder)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void redisLibrariesAreRelocatedOnceAndTheRelocatedCopyIsLoaded() throws Exception {
        byte[] jar = "netty".getBytes(StandardCharsets.UTF_8);
        RuntimeLibraries.Library library = new RuntimeLibraries.Library("REDIS", "io.netty", "netty-common", "4", sha(jar));
        Files.write(folder.resolve("netty-common-4.jar"), jar);
        List<Path> relocations = new ArrayList<>();
        List<Path> added = new ArrayList<>();
        RuntimeLibraries.Relocator relocator = (input, output) -> {
            relocations.add(input);
            Files.writeString(output, "relocated");
        };
        new RuntimeLibraries(List.of(library), folder, "https://repo.example/", (s, t) -> { }, added::add,
                NOPLogger.NOP_LOGGER, java.util.Set.of("REDIS"), relocator).ensure(List.of("REDIS"));
        new RuntimeLibraries(List.of(library), folder, "https://repo.example/", (s, t) -> { }, added::add,
                NOPLogger.NOP_LOGGER, java.util.Set.of("REDIS"), relocator).ensure(List.of("REDIS"));
        assertEquals(1, relocations.size());
        assertEquals(2, added.size());
        assertEquals(added.get(0), added.get(1));
        assertTrue(added.getFirst().startsWith(folder.resolve("relocated")));
        assertEquals("relocated", Files.readString(added.getFirst()));
    }

    @Test
    void theRelocationRulesMatchTheShadedPackages() {
        var rules = RuntimeLibraries.relocations();
        assertEquals("net.prismarin.libs.netty", rules.get("io.netty"));
        assertEquals("net.prismarin.libs.lettuce", rules.get("io.lettuce"));
        assertEquals("net.prismarin.libs.reactor", rules.get("reactor"));
        assertEquals("net.prismarin.libs.reactivestreams", rules.get("org.reactivestreams"));
    }
}
