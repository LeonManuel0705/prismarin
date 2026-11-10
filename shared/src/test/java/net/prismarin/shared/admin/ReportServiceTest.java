package net.prismarin.shared.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
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

    @Test
    void reportsAgainstTheSamePlayerAreBundled() {
        ReportService reports = new ReportService(new JsonFileDocumentStore(directory), Runnable::run,
                Clock.systemUTC());
        UUID target = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        UUID anna = UUID.randomUUID();
        ReportService.Submission first = reports.submit(new ReportService.Draft(alex, "Alex", target, "Steve",
                "Hacking", "bw-1", List.of(), "1.21.11"), 1_800_000L).join();
        ReportService.Submission again = reports.submit(new ReportService.Draft(alex, "Alex", target, "Steve",
                "Hacking", "bw-1", List.of(), ""), 1_800_000L).join();
        ReportService.Submission second = reports.submit(new ReportService.Draft(anna, "Anna", target, "Steve",
                "Teaming", "bw-1", List.of("> 12:00:00 Steve: hi"), ""), 1_800_000L).join();
        assertEquals(ReportService.Outcome.NEW, first.outcome());
        assertEquals(ReportService.Outcome.DUPLICATE, again.outcome());
        assertEquals(ReportService.Outcome.MERGED, second.outcome());
        assertEquals(first.report().id(), second.report().id());
        assertEquals(2, second.report().reporters());
        assertEquals("1.21.11", second.report().context());
        assertEquals(List.of("> 12:00:00 Steve: hi"), second.report().chat());
        assertEquals(1, reports.open().join().size());
    }

    @Test
    void punishingTheTargetClosesEveryReportAndQueuesFeedback() {
        ReportService reports = new ReportService(new JsonFileDocumentStore(directory), Runnable::run,
                Clock.systemUTC());
        UUID target = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        Report report = reports.file(alex, "Alex", target, "Steve", "fly hack", "bw-1").join();
        assertTrue(reports.claim(report.id(), "Leon").join().map(Report::claimedBy).filter("Leon"::equals).isPresent());
        List<Report> closed = reports.closeAllAgainst(target, "Leon", Report.PUNISHED).join();
        assertEquals(1, closed.size());
        assertEquals(Report.PUNISHED, closed.getFirst().verdict());
        assertTrue(reports.openAgainst(target).join().isEmpty());
        reports.queueFeedback(closed.getFirst().reporterIds()).join();
        reports.queueFeedback(List.of(alex)).join();
        assertEquals(2, reports.takeFeedback(alex).join());
        assertEquals(0, reports.takeFeedback(alex).join());
        assertFalse(reports.find(report.id()).join().orElseThrow().open());
    }
}
