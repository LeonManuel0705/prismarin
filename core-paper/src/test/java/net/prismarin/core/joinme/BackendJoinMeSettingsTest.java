package net.prismarin.core.joinme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.prismarin.shared.joinme.ArrivalPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackendJoinMeSettingsTest {

    @TempDir
    Path directory;

    @Test
    void theDefaultsPutPlayersIntoTheCreatorsWorld() throws IOException {
        Path file = directory.resolve(BackendJoinMeSettings.FILE_NAME);

        BackendJoinMeSettings settings = BackendJoinMeSettings.load(file);

        assertTrue(settings.problems().isEmpty(), settings.problems().toString());
        assertTrue(settings.sameWorld());
        assertEquals(ArrivalPlan.Position.SPAWN, settings.position());
        assertEquals(10, settings.delayTicks());
        assertTrue(settings.sendBackWhenRefused());
        assertTrue(settings.arrivalMessage());
        assertTrue(settings.navigatorEntry());
        assertEquals(Set.of(), settings.subCommands().off());
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.contains("# spawn: the spawn of that world. creator: right next to the creator."), text);
        assertTrue(text.contains("  preview: true"), text);
        assertTrue(settings.subCommands().on(JoinMeSubCommands.PREVIEW));
        assertEquals(settings, BackendJoinMeSettings.load(file));
    }

    @Test
    void switchesAndBrokenValuesAreRead() throws IOException {
        Path file = directory.resolve(BackendJoinMeSettings.FILE_NAME);
        Files.writeString(file, """
                fallback:
                  same-world: false
                  position: somewhere
                arrival:
                  delay-ticks: 9999
                sub-commands:
                  list: false
                  teleport: false
                lobby:
                  navigator-entry: false
                """, StandardCharsets.UTF_8);

        BackendJoinMeSettings settings = BackendJoinMeSettings.load(file);

        assertEquals(2, settings.problems().size(), settings.problems().toString());
        assertFalse(settings.sameWorld());
        assertEquals(ArrivalPlan.Position.SPAWN, settings.position());
        assertEquals(200, settings.delayTicks());
        assertEquals(Set.of("list"), settings.subCommands().off());
        assertFalse(settings.navigatorEntry());
        assertTrue(settings.subCommands().on("cancel"));
    }
}
