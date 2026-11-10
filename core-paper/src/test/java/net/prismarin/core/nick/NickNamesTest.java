package net.prismarin.core.nick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NickNamesTest {

    @Test
    void formatFollowsMinecraftNames() {
        assertTrue(NickNames.validFormat("Leon_07"));
        assertTrue(NickNames.validFormat("abc"));
        assertTrue(NickNames.validFormat("ABCDEFGHIJKLMNOP"));
        assertFalse(NickNames.validFormat("ab"));
        assertFalse(NickNames.validFormat("ABCDEFGHIJKLMNOPQ"));
        assertFalse(NickNames.validFormat("with space"));
        assertFalse(NickNames.validFormat("Umlaut_ä"));
        assertFalse(NickNames.validFormat(null));
    }

    @Test
    void blockedWordsAreMatchedInsideNamesIgnoringCase() {
        NickNames names = new NickNames(List.of(), true, List.of("Admin", "staff", " "));
        assertFalse(names.allowed("TheADMIN"));
        assertFalse(names.allowed("staffie"));
        assertTrue(names.allowed("Adm1n"));
    }

    @Test
    void generatedNamesAreAlwaysValid() {
        NickNames names = new NickNames(List.of(), true, List.of());
        Random random = new Random(42L);
        for (int i = 0; i < 5_000; i++) {
            String name = names.generate(random);
            assertTrue(NickNames.validFormat(name), name);
        }
    }

    @Test
    void generatorProducesVariety() {
        NickNames names = new NickNames(List.of(), true, List.of());
        Random random = new Random(7L);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(names.generate(random));
        }
        assertTrue(seen.size() > 150, "only " + seen.size() + " distinct names");
    }

    @Test
    void preferredNamesComeBeforeGeneratedOnes() {
        NickNames names = new NickNames(List.of("Alpha", "Bravo"), true, List.of());
        List<String> candidates = names.candidates(new Random(1L), 10);
        assertEquals(Set.of("Alpha", "Bravo"), Set.copyOf(candidates.subList(0, 2)));
        assertTrue(candidates.size() > 2);
    }

    @Test
    void chooseSkipsTakenAndBlockedNames() {
        NickNames names = new NickNames(List.of("Alpha", "Bravo", "AdminCat"), false, List.of("admin"));
        Optional<String> chosen = names.choose(name -> !name.equals("Alpha"), new Random(3L), 10);
        assertEquals(Optional.of("Bravo"), chosen);
    }

    @Test
    void chooseFailsWhenNothingIsFree() {
        NickNames names = new NickNames(List.of("Alpha"), false, List.of());
        assertEquals(Optional.empty(), names.choose(name -> false, new Random(3L), 10));
    }

    @Test
    void chooseFallsBackToTheGenerator() {
        NickNames names = new NickNames(List.of("Alpha"), true, List.of());
        Optional<String> chosen = names.choose(name -> !name.equals("Alpha"), new Random(5L), 10);
        assertTrue(chosen.isPresent());
        assertTrue(NickNames.validFormat(chosen.get()));
    }

    @Test
    void sameSeedGivesSameCandidates() {
        NickNames names = new NickNames(List.of("Alpha", "Bravo", "Charlie"), true, List.of());
        assertEquals(names.candidates(new Random(9L), 5), names.candidates(new Random(9L), 5));
    }
}
