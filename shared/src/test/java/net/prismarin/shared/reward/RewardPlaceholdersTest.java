package net.prismarin.shared.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RewardPlaceholdersTest {

    @Test
    void knownPlaceholdersAreReplacedAndOthersStay() {
        assertEquals("give Leon diamond 3 %unknown%", RewardPlaceholders.fill(
                "give %player% diamond %streak% %unknown%", Map.of("player", "Leon", "streak", "3")));
    }

    @Test
    void replacementsAreTakenLiterally() {
        assertEquals("say $1 \\o/", RewardPlaceholders.fill("say %player%", Map.of("player", "$1 \\o/")));
    }

    @Test
    void commandsLoseTheirSlash() {
        assertEquals("eco give Leon 5", RewardPlaceholders.command(" /eco give %player% 5 ", Map.of("player", "Leon")));
        assertEquals("", RewardPlaceholders.fill(null, Map.of()));
    }
}
