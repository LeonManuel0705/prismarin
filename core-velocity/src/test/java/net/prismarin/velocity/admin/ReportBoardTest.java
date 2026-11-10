package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.prismarin.shared.admin.Report;
import net.prismarin.velocity.social.Page;
import org.junit.jupiter.api.Test;

class ReportBoardTest {

    private static final long NOW = 10_000_000L;
    private static final long CLAIM = 600_000L;

    private static Report report(String id, long createdAt, int reporters, String holder, long claimedAt,
                                 boolean chat) {
        Report report = new Report(id, UUID.randomUUID(), "Alex", UUID.randomUUID(), "Steve" + id, "Hacking",
                "bw-1", createdAt, true, "", 0L, chat ? List.of("> 12:00:00 Steve: hi") : List.of());
        for (int i = 1; i < reporters; i++) {
            report = report.support(new Report.Supporter(UUID.randomUUID(), "R" + i, "Hacking", createdAt + i),
                    List.of());
        }
        return holder == null ? report : report.claim(holder, claimedAt);
    }

    private final List<Report> reports = List.of(
            report("1", 1_000L, 1, null, 0L, false),
            report("2", 2_000L, 4, "Mia", NOW - 60_000L, true),
            report("3", 3_000L, 2, "Leon", NOW - 30_000L, false),
            report("4", 4_000L, 1, "Mia", NOW - CLAIM, true),
            report("5", 5_000L, 4, null, 0L, false),
            report("6", 6_000L, 1, null, 0L, false).close("Leon", 7_000L));

    private static List<String> ids(List<Report> reports) {
        return reports.stream().map(Report::id).toList();
    }

    @Test
    void urgentFirstPutsMostReportersAndThenTheOldestCaseOnTop() {
        assertEquals(List.of("2", "5", "3", "1", "4"), ids(ReportBoard.arrange(reports, ReportBoard.Filter.ALL,
                ReportBoard.Sort.URGENT, "Leon", NOW, CLAIM)));
        assertEquals(List.of("5", "4", "3", "2", "1"), ids(ReportBoard.arrange(reports, ReportBoard.Filter.ALL,
                ReportBoard.Sort.NEWEST, "Leon", NOW, CLAIM)));
        assertEquals(List.of("1", "2", "3", "4", "5"), ids(ReportBoard.arrange(reports, ReportBoard.Filter.ALL,
                ReportBoard.Sort.OLDEST, "Leon", NOW, CLAIM)));
    }

    @Test
    void filtersKnowWhoWorksOnACaseAndWhenTheClaimRanOut() {
        assertEquals(List.of("1", "3", "4", "5"), ids(ReportBoard.arrange(reports, ReportBoard.Filter.FREE,
                ReportBoard.Sort.OLDEST, "Leon", NOW, CLAIM)));
        assertEquals(List.of("3"), ids(ReportBoard.arrange(reports, ReportBoard.Filter.MINE,
                ReportBoard.Sort.OLDEST, "leon", NOW, CLAIM)));
        assertEquals(List.of("2"), ids(ReportBoard.arrange(reports, ReportBoard.Filter.MINE,
                ReportBoard.Sort.OLDEST, "Mia", NOW, CLAIM)));
        assertEquals(List.of("2", "4"), ids(ReportBoard.arrange(reports, ReportBoard.Filter.CHAT,
                ReportBoard.Sort.OLDEST, "Leon", NOW, CLAIM)));
        assertEquals(List.of("1", "3", "5"), ids(ReportBoard.arrange(reports, ReportBoard.Filter.FREE,
                ReportBoard.Sort.OLDEST, "Leon", NOW, 0L)));
        ReportBoard.Counts counts = ReportBoard.count(reports, NOW, CLAIM, 3);
        assertEquals(new ReportBoard.Counts(5, 2, 2, 3), counts);
    }

    @Test
    void filtersAndSortsCycle() {
        assertEquals(ReportBoard.Filter.FREE, ReportBoard.Filter.ALL.next());
        assertEquals(ReportBoard.Filter.ALL, ReportBoard.Filter.CHAT.next());
        assertEquals(ReportBoard.Sort.URGENT, ReportBoard.Sort.OLDEST.next());
        assertEquals("newest", ReportBoard.Sort.NEWEST.key());
    }

    @Test
    void pagesHoldTwentyEightCasesAndClampTheRequestedPage() {
        List<Report> many = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            many.add(report(String.valueOf(100 + i), i, 1, null, 0L, false));
        }
        assertEquals(28, ReportsMenu.CASE_SLOTS.length);
        Page<Report> first = ReportBoard.page(many, 0, ReportsMenu.CASE_SLOTS.length);
        assertEquals(3, first.pages());
        assertEquals(28, first.entries().size());
        Page<Report> last = ReportBoard.page(many, 7, ReportsMenu.CASE_SLOTS.length);
        assertEquals(2, last.page());
        assertEquals(4, last.entries().size());
        assertEquals("156", last.entries().getFirst().id());
        Page<Report> empty = ReportBoard.page(List.of(), -3, 28);
        assertEquals(0, empty.page());
        assertEquals(1, empty.pages());
        assertTrue(empty.entries().isEmpty());
    }
}
