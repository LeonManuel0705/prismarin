package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.api.moderation.Report;
import net.prismarin.api.moderation.ReportAction;
import org.junit.jupiter.api.Test;

class ReportActionsTest {

    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000042");

    private static Report report() {
        return new Report("12345", TARGET, "Steve", "SilentFox", "bw-1", "", List.of(new Report.Reporter(
                UUID.randomUUID(), "Alex", "Hacking", 1L)), List.of(), 1L, "", 0L, true, "", 0L, null);
    }

    @Test
    void commandsGetTheCaseFilledIn() {
        assertEquals("spectate Steve 12345 bw-1 Leon " + TARGET,
                ReportActions.fill("spectate %player% %id% %server% %staff% %uuid%", report(), "Leon"));
        assertEquals("note %reason%", ReportActions.fill("note %reason%", report(), "Leon"));
    }

    @Test
    void actionButtonsAreCenteredOverTwoRows() {
        assertEquals(List.of(), ReportActions.slots(0));
        assertEquals(List.of(22), ReportActions.slots(1));
        assertEquals(List.of(20, 22, 24), ReportActions.slots(3));
        assertEquals(List.of(19, 21, 23, 25), ReportActions.slots(4));
        assertEquals(List.of(19, 20, 21, 22, 23, 24, 25), ReportActions.slots(7));
        assertEquals(List.of(19, 20, 21, 22, 23, 24, 25, 30, 32), ReportActions.slots(9));
        assertEquals(ReportActions.MAX, ReportActions.slots(30).size());
        assertTrue(ReportActions.slots(30).stream().allMatch(slot -> slot >= 18 && slot < 36));
    }

    @Test
    void labelsAcceptColourCodesAndTheViewersLanguage() {
        ReportAction action = new ReportAction("Spectate", "ENDER_EYE", Map.of("en", "&bSpectate", "de", "Zuschauen"),
                "", true, (staff, name, report) -> { });
        assertEquals("spectate", action.id());
        assertEquals("Spectate", PlainTextComponentSerializer.plainText().serialize(
                ReportActions.label(action, Locale.ENGLISH)));
        assertEquals("Zuschauen", action.name(Locale.GERMANY));
        assertThrows(IllegalArgumentException.class, () -> new ReportAction("a:b", null, Map.of(), null, true,
                (staff, name, report) -> { }));
    }
}
