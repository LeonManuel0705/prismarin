package net.prismarin.lobby.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocationsFileTest {

    @TempDir
    Path tempDir;

    @Test
    void seedsDefaultsWhenFileIsMissing() {
        Path file = tempDir.resolve("locations.yml");
        LocationsFile locations = LocationsFile.load(file);
        assertTrue(Files.exists(file));
        assertEquals(new SpawnPoint("world", 0.5, 4.0, 0.5, 0.0f, 0.0f), locations.spawn());
        assertEquals(0.0, locations.voidTeleportY());
    }

    @Test
    void savesAndReloadsSpawnPoint() {
        Path file = tempDir.resolve("locations.yml");
        LocationsFile locations = LocationsFile.load(file);
        SpawnPoint updated = new SpawnPoint("hub", 12.5, 65.0, -3.25, 90.0f, -12.5f);
        locations.spawn(updated);
        locations.save();
        LocationsFile reloaded = LocationsFile.load(file);
        assertEquals(updated, reloaded.spawn());
    }

    @Test
    void keepsVoidThresholdAcrossRoundtrip() throws IOException {
        Path file = tempDir.resolve("locations.yml");
        Files.writeString(file, """
                spawn:
                  world: "hub"
                  x: 1.5
                  y: 70.0
                  z: -8.5
                  yaw: 180.0
                  pitch: 45.0
                void-teleport-y: -12.0
                """);
        LocationsFile locations = LocationsFile.load(file);
        assertEquals(-12.0, locations.voidTeleportY());
        locations.save();
        LocationsFile reloaded = LocationsFile.load(file);
        assertEquals(-12.0, reloaded.voidTeleportY());
        assertEquals(new SpawnPoint("hub", 1.5, 70.0, -8.5, 180.0f, 45.0f), reloaded.spawn());
    }
}
