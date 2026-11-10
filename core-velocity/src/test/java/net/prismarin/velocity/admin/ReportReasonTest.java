package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportReasonTest {

    @Test
    void picksTheViewersLanguageThenEnglishThenTheFirstName() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("fr", "Triche");
        names.put("de", "Cheats");
        ReportReason reason = new ReportReason("Hacking", null, names);
        assertEquals("hacking", reason.id());
        assertEquals("PAPER", reason.icon());
        assertEquals("Cheats", reason.name(Locale.GERMANY));
        assertEquals("Triche", reason.name(Locale.ITALIAN));
        assertEquals("Hacking", new ReportReason("other", "BOOK", Map.of("en", "Hacking")).name(Locale.GERMAN));
        assertEquals("other", new ReportReason("other", "BOOK", Map.of()).name(Locale.GERMAN));
    }

    @Test
    void chatEvidenceFollowsTheConfiguredReasons() {
        AdminSettings.ChatEvidence evidence = new AdminSettings.ChatEvidence(true, 15, 40, false,
                List.of("Chat"), false);
        assertEquals(true, evidence.appliesTo("chat"));
        assertEquals(false, evidence.appliesTo("hacking"));
        assertEquals(false, evidence.appliesTo(null));
        assertEquals(900_000L, evidence.windowMillis());
    }
}
