package net.prismarin.core.essentials;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EssentialsSettingsTest {

    @TempDir
    Path directory;

    @Test
    void addsEveryCommandSwitchAndKeepsExistingValues() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "server-id: lobby\nessentials:\n  commands:\n    tpall: false\n");
        EssentialsSettings settings = EssentialsSettings.load(file);
        assertFalse(settings.enabled(EssentialsFeature.TPALL));
        assertTrue(settings.enabled(EssentialsFeature.SKULL));
        String text = Files.readString(file);
        assertTrue(text.contains("server-id: lobby"));
        for (EssentialsFeature feature : EssentialsFeature.values()) {
            assertTrue(text.contains(feature.key() + ":"), feature.key());
        }
        assertFalse(EssentialsSettings.load(file).enabled(EssentialsFeature.TPALL));
    }
}
