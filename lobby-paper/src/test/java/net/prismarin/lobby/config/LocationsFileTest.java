package net.prismarin.lobby.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocationsFileTest {

    @TempDir
    Path tempDir;

    @Test
    void missingFileMeansNoSpawnAndNoVoidTeleport() {
        Path file = tempDir.resolve("locations.yml");
        LocationsFile locations = LocationsFile.load(file);
        assertTrue(locations.spawns().isEmpty());
        assertTrue(locations.voidHeights().isEmpty());
        assertEquals(Optional.empty(), locations.spawn("world"));
        assertEquals(OptionalDouble.empty(), locations.voidHeight("world"));
        assertFalse(Files.exists(file));
    }

    @Test
    void ignoresTheSpawnThatOlderVersionsSeeded() throws IOException {
        Path file = tempDir.resolve("locations.yml");
        Files.writeString(file, """
                spawn:
                  world: "world"
                  x: 0.5
                  y: 4.0
                  z: 0.5
                  yaw: 0.0
                  pitch: 0.0
                void-teleport-y: 0.0
                """);
        LocationsFile locations = LocationsFile.load(file);
        assertTrue(locations.spawns().isEmpty());
        assertTrue(locations.voidHeights().isEmpty());
        String rewritten = Files.readString(file);
        assertFalse(rewritten.contains("void-teleport-y"));
        assertTrue(rewritten.startsWith("# Lobby spawns and void teleport heights per world."));
    }

    @Test
    void migratesAnOldSpawnThatWasSetByHand() throws IOException {
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
        SpawnPoint expected = new SpawnPoint("hub", 1.5, 70.0, -8.5, 180.0f, 45.0f);
        assertEquals(Optional.of(expected), locations.spawn("hub"));
        assertEquals(OptionalDouble.of(-12.0), locations.voidHeight("hub"));
        LocationsFile reloaded = LocationsFile.load(file);
        assertEquals(Optional.of(expected), reloaded.spawn("hub"));
        assertEquals(OptionalDouble.of(-12.0), reloaded.voidHeight("hub"));
        assertEquals(Optional.of("hub"), reloaded.firstSpawnWorld());
    }

    @Test
    void oldSeededVoidHeightDoesNotSwitchVoidTeleportOn() throws IOException {
        Path file = tempDir.resolve("locations.yml");
        Files.writeString(file, """
                spawn:
                  world: "hub"
                  x: 3.0
                  y: 80.0
                  z: 3.0
                void-teleport-y: 0.0
                """);
        LocationsFile locations = LocationsFile.load(file);
        assertTrue(locations.spawn("hub").isPresent());
        assertEquals(OptionalDouble.empty(), locations.voidHeight("hub"));
    }

    @Test
    void savesSpawnsAndVoidHeightsPerWorld() {
        Path file = tempDir.resolve("locations.yml");
        LocationsFile locations = LocationsFile.load(file);
        SpawnPoint lobby = new SpawnPoint("lobby", 12.5, 65.0, -3.25, 90.0f, -12.5f);
        SpawnPoint premium = new SpawnPoint("premium", 0.5, 100.0, 0.5, 0.0f, 0.0f);
        locations.spawn(lobby);
        locations.spawn(premium);
        locations.voidHeight("lobby", OptionalDouble.of(20.0));
        locations.save();
        LocationsFile reloaded = LocationsFile.load(file);
        assertEquals(List.of("lobby", "premium"), List.copyOf(reloaded.spawns().keySet()));
        assertEquals(Optional.of(lobby), reloaded.spawn("lobby"));
        assertEquals(Optional.of(premium), reloaded.spawn("premium"));
        assertEquals(OptionalDouble.of(20.0), reloaded.voidHeight("lobby"));
        assertEquals(OptionalDouble.empty(), reloaded.voidHeight("premium"));
    }

    @Test
    void removesSpawnsAndVoidHeights() {
        Path file = tempDir.resolve("locations.yml");
        LocationsFile locations = LocationsFile.load(file);
        locations.spawn(new SpawnPoint("lobby", 1.0, 2.0, 3.0, 0.0f, 0.0f));
        locations.voidHeight("lobby", OptionalDouble.of(-5.0));
        locations.save();
        assertTrue(locations.removeSpawn("lobby"));
        assertFalse(locations.removeSpawn("lobby"));
        locations.voidHeight("lobby", OptionalDouble.empty());
        locations.save();
        LocationsFile reloaded = LocationsFile.load(file);
        assertTrue(reloaded.spawns().isEmpty());
        assertTrue(reloaded.voidHeights().isEmpty());
    }
}
