package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ReportMessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-z_]+%");

    @Test
    void bothLanguagesHaveTheSameReportTexts() throws IOException {
        Map<String, String> german = PunishmentMessagesTest.texts("messages_de.yml", "reports");
        Map<String, String> english = PunishmentMessagesTest.texts("messages_en.yml", "reports");
        assertEquals(german.keySet(), english.keySet());
        german.forEach((key, text) -> {
            assertEquals(placeholders(text), placeholders(english.get(key)), key);
            assertFalse(text.contains("—"), key);
            assertFalse(text.contains("–"), key);
        });
    }

    @Test
    void everyMenuTextExists() throws IOException {
        Map<String, String> english = PunishmentMessagesTest.texts("messages_en.yml", "reports");
        for (String key : Set.of("status.free", "status.mine", "status.other", "still-yours", "list-previous",
                "list-next", "menu.title", "menu.summary", "menu.case", "menu.case-urgent", "menu.case-title",
                "menu.reporter-line", "menu.status-timeout", "menu.take-over-hint", "menu.back", "menu.done",
                "menu.reject", "menu.punish", "menu.goto", "menu.record-button", "menu.chat-title")) {
            assertTrue(english.containsKey(key), key);
        }
        for (ReportBoard.Sort sort : ReportBoard.Sort.values()) {
            assertTrue(english.containsKey("menu.sort-" + sort.key()), sort.key());
        }
        for (ReportBoard.Filter filter : ReportBoard.Filter.values()) {
            assertTrue(english.containsKey("menu.filter-" + filter.key()), filter.key());
        }
    }

    private static Set<String> placeholders(String text) {
        return Set.copyOf(PLACEHOLDER.matcher(text).results().map(MatchResult::group).toList());
    }
}
