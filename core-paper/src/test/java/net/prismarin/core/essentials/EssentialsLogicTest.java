package net.prismarin.core.essentials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EssentialsLogicTest {

    @Test
    void gameModesAcceptNumbersLettersAndNames() {
        assertEquals(Optional.of(GameModeChoice.SURVIVAL), GameModeChoice.parse("0"));
        assertEquals(Optional.of(GameModeChoice.SURVIVAL), GameModeChoice.parse("s"));
        assertEquals(Optional.of(GameModeChoice.CREATIVE), GameModeChoice.parse("C"));
        assertEquals(Optional.of(GameModeChoice.ADVENTURE), GameModeChoice.parse("adventure"));
        assertEquals(Optional.of(GameModeChoice.SPECTATOR), GameModeChoice.parse("sp"));
        assertEquals(Optional.of(GameModeChoice.SPECTATOR), GameModeChoice.parse("3"));
        assertEquals(Optional.of(GameModeChoice.CREATIVE), GameModeChoice.parse("gmc"));
        assertEquals(Optional.of(GameModeChoice.SPECTATOR), GameModeChoice.parse("gmsp"));
        assertTrue(GameModeChoice.parse("4").isEmpty());
        assertTrue(GameModeChoice.parse("fly").isEmpty());
        assertTrue(GameModeChoice.parse(null).isEmpty());
    }

    @Test
    void speedLevelsMapLikeEssentials() {
        assertEquals(0.2F, SpeedLevels.value(1, false), 1e-6);
        assertEquals(0.1F, SpeedLevels.value(1, true), 1e-6);
        assertEquals(1.0F, SpeedLevels.value(10, false), 1e-6);
        assertEquals(1.0F, SpeedLevels.value(10, true), 1e-6);
        assertEquals(0.2F + 4 / 9F * 0.8F, SpeedLevels.value(5, false), 1e-6);
        assertEquals(1.0F, SpeedLevels.value(99, false), 1e-6);
        assertEquals(Optional.of(1), SpeedLevels.parse("reset"));
        assertEquals(Optional.of(7), SpeedLevels.parse(" 7 "));
        assertTrue(SpeedLevels.parse("0").isEmpty());
        assertTrue(SpeedLevels.parse("11").isEmpty());
        assertTrue(SpeedLevels.parse("fast").isEmpty());
    }

    @Test
    void everyCommandBelongsToExactlyOneFeature() {
        Set<String> seen = new HashSet<>();
        for (EssentialsFeature feature : EssentialsFeature.values()) {
            for (String command : feature.commands()) {
                assertTrue(seen.add(command), command);
                assertEquals(Optional.of(feature), EssentialsFeature.byCommand(command.toUpperCase()));
            }
        }
        assertEquals("prismarin.essentials.gamemode.others", EssentialsFeature.GAMEMODE.permission("others"));
        assertTrue(EssentialsFeature.byCommand("clear").isEmpty());
    }
}
