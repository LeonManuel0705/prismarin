package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.format.NamedTextColor;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.api.moderation.ReportOutcome;
import net.prismarin.shared.admin.ReportService;
import org.junit.jupiter.api.Test;

class ProxyPunishmentTemplatesTest {

    @Test
    void templatesSurviveTheWayThroughTheApi() {
        PunishmentTemplate template = new PunishmentTemplate("chat", Map.of("de", "Chat", "en", "Chat"), "BOOK",
                "prismarin.punish.template.chat", List.of(new PunishmentTemplate.Step(PunishmentType.WARN, null),
                new PunishmentTemplate.Step(PunishmentType.MUTE, Duration.ofHours(2)),
                new PunishmentTemplate.Step(PunishmentType.BAN, null)), Duration.ofDays(90),
                Map.of("en", "Insults only"));
        net.prismarin.api.moderation.PunishmentTemplate api = ProxyPunishmentTemplates.toApi(template);
        assertEquals("chat", api.id());
        assertEquals(3, api.steps().size());
        assertEquals(Duration.ofHours(2), api.steps().get(1).duration());
        assertEquals("Insults only", api.description(null));
        assertEquals(template, ProxyPunishmentTemplates.fromApi(api));
    }

    @Test
    void reasonsAndOutcomesAreTranslated() {
        net.prismarin.api.moderation.ReportReason reason = ProxyReportApi.toApi(new ReportReason("hacking",
                "DIAMOND_SWORD", Map.of("en", "Hacking"), NamedTextColor.RED));
        assertEquals("#FF5555", reason.color().toUpperCase(java.util.Locale.ROOT));
        assertEquals("", ProxyReportApi.toApi(new ReportReason("other", "BOOK", Map.of())).color());
        assertEquals(ReportOutcome.FILED, ProxyReportApi.outcome(ReportService.Outcome.NEW));
        assertEquals(ReportOutcome.MERGED, ProxyReportApi.outcome(ReportService.Outcome.MERGED));
        assertEquals(ReportOutcome.DUPLICATE, ProxyReportApi.outcome(ReportService.Outcome.DUPLICATE));
    }
}
