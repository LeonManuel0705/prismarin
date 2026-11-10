package net.prismarin.api.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportModelTest {

    private static final UUID TARGET = UUID.randomUUID();

    private static Report report(boolean open, String holder, ReportVerdict verdict) {
        return new Report("1", TARGET, "Steve", null, "bw-1", null,
                List.of(new Report.Reporter(UUID.randomUUID(), "Alex", "Hacking", 1L),
                        new Report.Reporter(UUID.randomUUID(), "Anna", "Teaming", 2L)),
                null, 1L, holder, 5L, open, null, 0L, verdict);
    }

    @Test
    void reportsDescribeTheirState() {
        Report open = report(true, "Leon", ReportVerdict.VALID);
        assertEquals("Steve", open.shownName());
        assertEquals("Hacking", open.reason());
        assertEquals(2, open.reporterCount());
        assertTrue(open.inProgress());
        assertNull(open.verdict());
        assertTrue(open.closedAs().isEmpty());
        Report closed = report(false, "Leon", ReportVerdict.PUNISHED);
        assertFalse(closed.inProgress());
        assertEquals(ReportVerdict.PUNISHED, closed.closedAs().orElseThrow());
        assertFalse(report(true, null, null).inProgress());
        assertThrows(IllegalArgumentException.class, () -> new Report("1", TARGET, "Steve", "", "", "", List.of(),
                List.of(), 0L, "", 0L, true, "", 0L, null));
    }

    @Test
    void verdictsHaveStableIds() {
        assertEquals("punished", ReportVerdict.PUNISHED.id());
        assertEquals(ReportVerdict.INVALID, ReportVerdict.byId(" Invalid ").orElseThrow());
        assertTrue(ReportVerdict.byId("maybe").isEmpty());
        assertTrue(ReportVerdict.byId(null).isEmpty());
    }

    @Test
    void reasonsActionsAndTemplatesValidateTheirIds() {
        ReportReason reason = new ReportReason(" XRay ", null, Map.of("de", "Röntgenblick", "en", "X-ray"), null);
        assertEquals("xray", reason.id());
        assertEquals("PAPER", reason.icon());
        assertEquals("", reason.color());
        assertEquals("Röntgenblick", reason.name(Locale.GERMANY));
        assertEquals("X-ray", reason.name(Locale.FRENCH));
        assertThrows(IllegalArgumentException.class, () -> new ReportReason("two words", null, Map.of(), ""));
        ReportAction action = new ReportAction("freeze", null, Map.of(), null, false, (staff, name, report) -> { });
        assertEquals("freeze", action.name(Locale.ENGLISH));
        assertEquals("", action.permission());
        assertThrows(NullPointerException.class, () -> new ReportAction("freeze", null, Map.of(), null, false, null));
        PunishmentTemplate template = new PunishmentTemplate("Caps", Map.of("en", "Caps"), null, null, null,
                List.of(new PunishmentTemplate.Step(PunishmentType.WARN, Duration.ofHours(1)),
                        new PunishmentTemplate.Step(PunishmentType.MUTE, null)), Duration.ZERO);
        assertEquals("caps", template.id());
        assertNull(template.steps().getFirst().duration());
        assertFalse(template.steps().getFirst().permanent());
        assertTrue(template.steps().getLast().permanent());
        assertNull(template.forgetAfter());
        assertEquals("", template.description(Locale.ENGLISH));
        assertThrows(IllegalArgumentException.class, () -> new PunishmentTemplate("empty", Map.of(), Map.of(), null,
                null, List.of(), null));
    }

    @Test
    void theApiIsEmptyUntilTheProxyRegistersIt() {
        ModerationApi.unregister();
        assertTrue(ModerationApi.reports().isEmpty());
        assertTrue(ModerationApi.templates().isEmpty());
    }
}
