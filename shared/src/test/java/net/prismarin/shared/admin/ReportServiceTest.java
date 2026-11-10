package net.prismarin.shared.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportServiceTest {

    @TempDir
    Path directory;

    @Test
    void reportsCanBeFiledListedAndClosed() {
        ReportService reports = new ReportService(new JsonFileDocumentStore(directory), Runnable::run,
                Clock.systemUTC());
        UUID target = UUID.randomUUID();
        Report report = reports.file(UUID.randomUUID(), "Alex", target, "Steve", "fly hack", "bw-1").join();
        assertEquals(1, reports.open().join().size());
        assertEquals(1L, reports.countAgainst(target).join());
        assertTrue(reports.close(report.id(), "Leon").join().isPresent());
        assertTrue(reports.open().join().isEmpty());
        assertTrue(reports.close(report.id(), "Leon").join().isEmpty());
    }
}
