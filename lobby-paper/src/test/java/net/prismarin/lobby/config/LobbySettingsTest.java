package net.prismarin.lobby.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LobbySettingsTest {

    @TempDir
    Path directory;

    @Test
    void freshFileEnablesEverything() throws IOException {
        Path file = directory.resolve("config.yml");
        LobbySettings settings = LobbySettings.load(file);
        LobbySettings.ALL.forEach(feature -> assertTrue(settings.enabled(feature)));
        assertTrue(Files.readString(file).contains("double-jump: true"));
    }

    @Test
    void switchedOffFeaturesStayOffAndMissingOnesAreAdded() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "features:\n  double-jump: false\n  sidebar: false\n");
        LobbySettings settings = LobbySettings.load(file);
        assertFalse(settings.enabled(LobbySettings.DOUBLE_JUMP));
        assertFalse(settings.enabled(LobbySettings.SIDEBAR));
        assertTrue(settings.enabled(LobbySettings.NAVIGATOR));
        assertTrue(Files.readString(file).contains("navigator: true"));
    }
}
