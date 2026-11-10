package net.prismarin.shared.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
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

    @Test
    void claimsRunOutWhenNobodyTouchesTheCase() {
        AtomicLong now = new AtomicLong(1_000_000L);
        ReportService reports = new ReportService(new JsonFileDocumentStore(directory), Runnable::run,
                fixed(now));
        Report report = reports.file(UUID.randomUUID(), "Alex", UUID.randomUUID(), "Steve", "Hacking", "bw-1").join();
        long timeout = 600_000L;
        ReportService.Claim first = reports.claim(report.id(), "Leon", timeout, false).join().orElseThrow();
        assertEquals(ReportService.ClaimOutcome.CLAIMED, first.outcome());
        assertEquals("Leon", first.report().holder(now.get(), timeout).orElseThrow());
        now.addAndGet(300_000L);
        ReportService.Claim other = reports.claim(report.id(), "Mia", timeout, false).join().orElseThrow();
        assertEquals(ReportService.ClaimOutcome.HELD, other.outcome());
        assertEquals("Leon", other.report().claimedBy());
        ReportService.Claim again = reports.claim(report.id(), "leon", timeout, false).join().orElseThrow();
        assertEquals(ReportService.ClaimOutcome.KEPT, again.outcome());
        now.addAndGet(599_000L);
        assertTrue(reports.find(report.id()).join().orElseThrow().heldBy("Leon", now.get(), timeout));
        now.addAndGet(1_000L);
        Report idle = reports.find(report.id()).join().orElseThrow();
        assertTrue(idle.holder(now.get(), timeout).isEmpty());
        assertEquals(ReportService.ClaimOutcome.CLAIMED,
                reports.claim(report.id(), "Mia", timeout, false).join().orElseThrow().outcome());
        assertEquals(ReportService.ClaimOutcome.CLAIMED,
                reports.claim(report.id(), "Leon", timeout, true).join().orElseThrow().outcome());
        assertTrue(reports.find(report.id()).join().orElseThrow().holder(now.get() + 10 * timeout, 0L).isPresent());
        reports.close(report.id(), "Leon").join();
        assertTrue(reports.claim(report.id(), "Leon", timeout, true).join().isEmpty());
    }

    @Test
    void claimDecisionsFollowTheHolderAndTheTimeout() {
        Report report = new Report("1", UUID.randomUUID(), "Alex", UUID.randomUUID(), "Steve", "Hacking", "bw-1",
                0L, true, "", 0L, List.of()).claim("Leon", 1_000L);
        assertEquals(ReportService.ClaimOutcome.HELD, ReportService.decide(report, "Mia", 5_000L, 10_000L, false));
        assertEquals(ReportService.ClaimOutcome.CLAIMED, ReportService.decide(report, "Mia", 5_000L, 10_000L, true));
        assertEquals(ReportService.ClaimOutcome.KEPT, ReportService.decide(report, "LEON", 5_000L, 10_000L, false));
        assertEquals(ReportService.ClaimOutcome.CLAIMED, ReportService.decide(report, "Mia", 11_000L, 10_000L, false));
        assertEquals(ReportService.ClaimOutcome.HELD, ReportService.decide(report, "Mia", 99_000L, 0L, false));
        assertTrue(report.close("Leon", 2_000L).holder(2_000L, 10_000L).isEmpty());
    }

    @Test
    void mergedCasesListEveryDistinctReason() {
        Report report = new Report("1", UUID.randomUUID(), "Alex", UUID.randomUUID(), "Steve", "Hacking", "bw-1",
                10L, true, "", 0L, List.of())
                .support(new Report.Supporter(UUID.randomUUID(), "Anna", "hacking", 20L), List.of())
                .support(new Report.Supporter(UUID.randomUUID(), "Mia", "Teaming", 30L), List.of());
        assertEquals(List.of("Hacking", "Teaming"), report.reasons());
        assertEquals(30L, report.lastReportAt());
        assertEquals(3, report.reporters());
    }

    @Test
    void idleClaimsAreReleasedAndNamedOnce() {
        AtomicLong now = new AtomicLong(1_000_000L);
        ReportService reports = new ReportService(new JsonFileDocumentStore(directory), Runnable::run, fixed(now));
        Report first = reports.file(UUID.randomUUID(), "Alex", UUID.randomUUID(), "Steve", "Hacking", "bw-1").join();
        Report second = reports.file(UUID.randomUUID(), "Anna", UUID.randomUUID(), "Tim", "Spam", "bw-2").join();
        reports.claim(first.id(), "Leon", 600_000L, false).join();
        now.addAndGet(500_000L);
        ReportService.Claim taken = reports.claim(second.id(), "Mia", 600_000L, false).join().orElseThrow();
        assertEquals("", taken.previous());
        assertTrue(reports.releaseIdle(600_000L).join().isEmpty());
        now.addAndGet(100_000L);
        List<ReportService.Release> released = reports.releaseIdle(600_000L).join();
        assertEquals(1, released.size());
        assertEquals("Leon", released.getFirst().staff());
        assertEquals("", released.getFirst().report().claimedBy());
        assertTrue(reports.releaseIdle(600_000L).join().isEmpty());
        assertTrue(reports.releaseIdle(0L).join().isEmpty());
        assertEquals("Mia", reports.release(second.id()).join().orElseThrow().staff());
        assertEquals("", reports.release(second.id()).join().orElseThrow().staff());
        ReportService.Claim over = reports.claim(first.id(), "Mia", 600_000L, false).join().orElseThrow();
        assertEquals("", over.previous());
        assertEquals("Mia", reports.claim(first.id(), "Leon", 600_000L, true).join().orElseThrow().previous());
    }

    private static Clock fixed(AtomicLong now) {
        return new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(now.get());
            }
        };
    }
}
