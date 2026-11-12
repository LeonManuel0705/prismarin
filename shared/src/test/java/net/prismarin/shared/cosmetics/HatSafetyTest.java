package net.prismarin.shared.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.prismarin.shared.cosmetics.HatSafety.Treatment;
import org.junit.jupiter.api.Test;

class HatSafetyTest {

    static final class Recorder implements HatSafety.Editor {

        final List<String> steps = new ArrayList<>();

        @Override
        public void neutralizeAttributes() {
            steps.add("attributes");
        }

        @Override
        public void removeEnchantments() {
            steps.add("enchantments");
        }

        @Override
        public void disguise(String model, String asset) {
            steps.add("disguise " + model + " " + asset);
        }
    }

    @Test
    void everyHatLosesArmorAttributesAndEnchantments() {
        for (String material : List.of("iron_helmet", "diamond_helmet", "netherite_helmet", "glass", "dragon_head",
                "player_head", "minecraft:Sea_Lantern")) {
            Recorder recorder = new Recorder();
            HatSafety.apply(HatSafety.check(material), recorder);
            assertEquals(List.of("attributes", "enchantments"), recorder.steps, material);
        }
        assertEquals("sea_lantern", HatSafety.check("minecraft:Sea_Lantern").material());
    }

    @Test
    void aTurtleShellIsRefusedBecauseItGivesWaterBreathing() {
        HatSafety.Verdict turtle = HatSafety.check("turtle_helmet");

        assertEquals(Treatment.REFUSE, turtle.treatment());
        assertFalse(turtle.usable());
        assertTrue(turtle.reason().contains("water breathing"));
        assertThrows(IllegalArgumentException.class, () -> HatSafety.apply(turtle, new Recorder()));
    }

    @Test
    void aCarvedPumpkinBecomesAJackOLantern() {
        HatSafety.Verdict pumpkin = HatSafety.check("carved_pumpkin");

        assertEquals(Treatment.REPLACE, pumpkin.treatment());
        assertEquals("jack_o_lantern", pumpkin.material());
        assertTrue(pumpkin.reason().contains("blurs"));
        assertEquals(Treatment.KEEP, HatSafety.check("jack_o_lantern").treatment());
    }

    @Test
    void goldenArmorKeepsItsLookOnANeutralItemSoPiglinsStayHostile() {
        HatSafety.Verdict crown = HatSafety.check("golden_helmet");
        Recorder recorder = new Recorder();

        HatSafety.apply(crown, recorder);

        assertEquals(Treatment.DISGUISE, crown.treatment());
        assertEquals(HatSafety.DISGUISE_BASE, crown.material());
        assertEquals("", crown.reason());
        assertEquals(List.of("attributes", "enchantments", "disguise golden_helmet gold"), recorder.steps);
    }

    @Test
    void mobHeadsThatChangeHowMobsSeeTheWearerAreReported() {
        for (String head : List.of("zombie_head", "skeleton_skull", "creeper_head", "piglin_head")) {
            HatSafety.Verdict verdict = HatSafety.check(head);
            assertEquals(Treatment.WARN, verdict.treatment(), head);
            assertTrue(verdict.usable(), head);
            assertTrue(verdict.reason().contains("notice the wearer later"), head);
        }
        assertEquals(Treatment.KEEP, HatSafety.check("wither_skeleton_skull").treatment());
        assertEquals(Treatment.KEEP, HatSafety.check("dragon_head").treatment());
    }
}
