package net.prismarin.velocity.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyConfigCodecTest {

    @TempDir
    Path directory;

    @Test
    void loadWritesDefaultsWhenFileMissing() {
        Path file = directory.resolve("proxy.yml");
        ProxyConfig config = ProxyConfigCodec.load(file);
        assertTrue(Files.exists(file));
        assertFalse(config.maintenance());
        assertEquals("lobby", config.lobbyServer());
        assertEquals(200, config.maxPlayers());
        assertEquals("Wartung", config.maintenanceVersionName());
        assertTrue(config.motdLine1().contains("%brand%"));
        assertTrue(config.motdLine2().contains("Lobby, Spiele"));
    }

    @Test
    void roundTripsMaintenanceState() {
        Path file = directory.resolve("proxy.yml");
        ProxyConfig defaults = ProxyConfigCodec.load(file);
        ProxyConfigCodec.save(file, defaults.withMaintenance(true));
        ProxyConfig reloaded = ProxyConfigCodec.load(file);
        assertTrue(reloaded.maintenance());
        assertEquals(defaults.motdLine1(), reloaded.motdLine1());
        assertEquals(defaults.motdLine2(), reloaded.motdLine2());
        assertEquals(defaults.maintenanceMotdLine1(), reloaded.maintenanceMotdLine1());
        assertEquals(defaults.maintenanceMotdLine2(), reloaded.maintenanceMotdLine2());
        assertEquals(defaults.maintenanceVersionName(), reloaded.maintenanceVersionName());
        assertEquals(defaults.maxPlayers(), reloaded.maxPlayers());
        assertEquals(defaults.lobbyServer(), reloaded.lobbyServer());
    }

    @Test
    void withMaintenanceOnlyTogglesState() {
        ProxyConfig defaults = ProxyConfigCodec.defaults();
        ProxyConfig active = defaults.withMaintenance(true);
        assertTrue(active.maintenance());
        assertEquals(defaults, active.withMaintenance(false));
    }
}
