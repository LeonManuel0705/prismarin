package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AltSettingsTest {

    @TempDir
    Path directory;

    @Test
    void defaultsOnlyAlertStaff() throws IOException {
        Path file = directory.resolve(AltSettings.FILE_NAME);
        AltSettings settings = AltSettings.load(file);
        assertEquals(AltSettings.defaults(), settings);
        assertTrue(settings.alertStaff());
        assertFalse(settings.enforceBans());
        assertEquals(Duration.ofDays(90).toMillis(), settings.retentionMillis());
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.contains("# Alt account detection on the proxy."));
        assertTrue(written.contains("enforce-bans: false"));
        assertTrue(written.contains("never the address itself"));
    }

    @Test
    void ownValuesAreReadAndCheckedValuesClamped() throws IOException {
        Path file = directory.resolve(AltSettings.FILE_NAME);
        Files.writeString(file, String.join("\n", "max-addresses-per-player: 0", "shared-address-limit: 5",
                "retention: forever", "alert-staff: false", "enforce-bans: true", ""), StandardCharsets.UTF_8);
        AltSettings settings = AltSettings.load(file);
        assertEquals(1, settings.maxAddresses());
        assertEquals(5, settings.sharedLimit());
        assertEquals(Duration.ofDays(90).toMillis(), settings.retentionMillis());
        assertFalse(settings.alertStaff());
        assertTrue(settings.enforceBans());
        assertEquals(1, settings.problems().size());
    }

    @Test
    void brokenFilesKeepTheDefaults() throws IOException {
        Path file = directory.resolve(AltSettings.FILE_NAME);
        Files.writeString(file, "retention: [oops", StandardCharsets.UTF_8);
        AltSettings settings = AltSettings.load(file);
        assertEquals(List.of(AltSettings.FILE_NAME), settings.problems().stream()
                .map(problem -> problem.substring(0, AltSettings.FILE_NAME.length())).toList());
        assertFalse(settings.enforceBans());
    }
}
