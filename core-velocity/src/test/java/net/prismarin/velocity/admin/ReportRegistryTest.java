package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.prismarin.api.moderation.ReportAction;
import org.junit.jupiter.api.Test;

class ReportRegistryTest {

    private static ReportAction action(String id) {
        return new ReportAction(id, "PAPER", Map.of("en", id), "", true, (staff, name, report) -> { });
    }

    @Test
    void pluginsAddReasonsAndActionsAfterTheConfiguredOnes() {
        ReportRegistry registry = new ReportRegistry(AdminSettings.DEFAULT_REASONS, List.of(action("spectate")));
        registry.register(new ReportReason("xray", "DIAMOND_ORE", Map.of("en", "X-ray")));
        registry.register(action("freeze"));
        assertEquals("xray", registry.reasons().getLast().id());
        assertEquals(AdminSettings.DEFAULT_REASONS.size() + 1, registry.reasons().size());
        assertEquals(List.of("spectate", "freeze"), registry.actions().stream().map(ReportAction::id).toList());
        assertEquals("X-ray", registry.reason("XRAY").orElseThrow().name(null));
        registry.register(new ReportReason("xray", "STONE", Map.of("en", "Xray")));
        assertEquals("STONE", registry.reason("xray").orElseThrow().icon());
        registry.unregisterReason("XRay");
        registry.unregisterAction("freeze");
        assertTrue(registry.reason("xray").isEmpty());
        assertEquals(List.of("spectate"), registry.actions().stream().map(ReportAction::id).toList());
    }

    @Test
    void configuredEntriesCannotBeReplaced() {
        ReportRegistry registry = new ReportRegistry(AdminSettings.DEFAULT_REASONS, List.of(action("spectate")));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new ReportReason("hacking", "STONE", Map.of())));
        assertThrows(IllegalArgumentException.class, () -> registry.register(action("spectate")));
        registry.unregisterReason("hacking");
        assertTrue(registry.reason("hacking").isPresent());
        assertTrue(registry.reason(null).isEmpty());
    }
}
