package net.prismarin.shared.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditLogTest {

    @TempDir
    Path directory;

    @Test
    void latestEntriesComeFirstAndFilterByTarget() {
        JsonFileDocumentStore store = new JsonFileDocumentStore(directory);
        new AuditLog(store, Runnable::run, Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC))
                .record("Leon", "ban", "Steve", "Hacking");
        new AuditLog(store, Runnable::run, Clock.fixed(Instant.ofEpochMilli(2000L), ZoneOffset.UTC))
                .record("Leon", "send", "Alex", "lobby");
        AuditLog log = new AuditLog(store, Runnable::run, Clock.systemUTC());
        List<AuditLog.Entry> all = log.latest(null, 10).join();
        assertEquals("send", all.getFirst().action());
        assertEquals(1, log.latest("Steve", 10).join().size());
    }
}
