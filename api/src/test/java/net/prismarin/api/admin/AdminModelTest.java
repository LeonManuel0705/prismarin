package net.prismarin.api.admin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdminModelTest {

    private final NoticeText text = NoticeText.of("hello");

    @Test
    void staffAlertsDefaultToTheAlertPermission() {
        StaffAlert alert = StaffAlert.of(" AntiCheat ", text);

        assertEquals("AntiCheat", alert.source());
        assertEquals(StaffAlert.DEFAULT_PERMISSION, alert.permission());
        assertEquals("myplugin.alerts", alert.permission("MyPlugin.Alerts").permission());
        assertThrows(IllegalArgumentException.class, () -> StaffAlert.of("", text));
        assertThrows(IllegalArgumentException.class, () -> StaffAlert.of("x".repeat(33), text));
        assertThrows(IllegalArgumentException.class, () -> alert.permission("bad permission"));
        assertThrows(NullPointerException.class, () -> StaffAlert.of("AntiCheat", (NoticeText) null));
    }

    @Test
    void broadcastScopesAreNormalised() {
        assertTrue(Broadcast.all(text).everyone());
        assertEquals("all", new Broadcast(null, text).scope());
        assertEquals(Optional.of("bw-1"), Broadcast.server("BW-1", text).targetServer());
        assertEquals(Optional.of("bedwars"), Broadcast.group("bedwars", text).targetGroup());
        assertEquals(Optional.empty(), Broadcast.group("bedwars", text).targetServer());
        for (String invalid : new String[] {"everyone", "server:", "group:a b", "server:a:b"}) {
            assertThrows(IllegalArgumentException.class, () -> new Broadcast(invalid, text), invalid);
        }
    }

    @Test
    void maintenanceStateParsesTargets() {
        MaintenanceState state = MaintenanceState.of(true, List.of("server:BW-1", "group:bedwars", "nonsense",
                "server:", "group:skywars"));

        assertTrue(state.network());
        assertEquals(Set.of("bw-1"), state.servers());
        assertEquals(Set.of("bedwars", "skywars"), state.groups());
        assertEquals(List.of("server:bw-1", "group:bedwars", "group:skywars"), state.targets());
        assertTrue(state.blocks("BW-1", "lobby"));
        assertTrue(state.blocks("bw-9", "Bedwars"));
        assertFalse(state.blocks("lobby-1", "lobby"));
        assertFalse(MaintenanceState.OPEN.anything());
        assertEquals("lobby-1", MaintenanceState.name(" Lobby-1 "));
        assertThrows(IllegalArgumentException.class, () -> MaintenanceState.name("a:b"));
    }

    @Test
    void auditInputIsChecked() {
        assertDoesNotThrow(() -> AuditEntry.check("AntiCheat", "anticheat.flag", "Leon", "Reach 4.2"));
        assertDoesNotThrow(() -> AuditEntry.check("AntiCheat", "flag", null, null));
        assertThrows(IllegalArgumentException.class, () -> AuditEntry.check("", "flag", "", ""));
        assertThrows(IllegalArgumentException.class, () -> AuditEntry.check("x".repeat(65), "flag", "", ""));
        assertThrows(IllegalArgumentException.class, () -> AuditEntry.check("A", "Flag", "", ""));
        assertThrows(IllegalArgumentException.class, () -> AuditEntry.check("A", "two words", "", ""));
        assertThrows(IllegalArgumentException.class, () -> AuditEntry.check("A", "flag", "x".repeat(129), ""));
        assertThrows(IllegalArgumentException.class, () -> AuditEntry.check("A", "flag", "", "x".repeat(1025)));
        assertEquals(1, AuditEntry.limit(-5));
        assertEquals(AuditEntry.MAX_LIMIT, AuditEntry.limit(10_000));
        assertEquals("", new AuditEntry(null, 1L, null, null, null, null).details());
    }

    @Test
    void staffChatNeedsAuthorAndMessage() {
        StaffChat chat = new StaffChat(" AntiCheat ", " Leon flagged ");

        assertEquals("AntiCheat", chat.author());
        assertEquals("Leon flagged", chat.message());
        assertThrows(IllegalArgumentException.class, () -> new StaffChat("", "x"));
        assertThrows(IllegalArgumentException.class, () -> new StaffChat("a", " "));
        assertThrows(IllegalArgumentException.class, () -> new StaffChat("a", "x".repeat(257)));
    }
}
