package net.prismarin.core.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticHat;
import net.prismarin.api.cosmetics.CosmeticParticles;
import net.prismarin.api.cosmetics.CosmeticRarity;
import net.prismarin.api.cosmetics.CosmeticSlot;
import net.prismarin.api.cosmetics.CosmeticSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CosmeticPlaceholdersTest {

    private final UUID player = UUID.randomUUID();
    private StubCosmetics cosmetics;
    private CosmeticPlaceholders placeholders;

    @BeforeEach
    void setUp() {
        cosmetics = new StubCosmetics()
                .add(Cosmetic.particles("flame-trail", CosmeticSlot.TRAIL, "Flame trail",
                        CosmeticParticles.of("point", "flame")))
                .add(Cosmetic.particles("kill-hearts", CosmeticSlot.KILL_EFFECT, "Hearts",
                        CosmeticParticles.of("heart", "dust")).withRarity("rare"))
                .add(Cosmetic.hat("crown", "Crown", CosmeticHat.item("golden_helmet")).withPermission("vip.crown"))
                .add(Cosmetic.hat("secret", "Secret", CosmeticHat.item("tnt")).withHidden(true));
        cosmetics.rarities.add(new CosmeticRarity("rare", NoticeText.of(Map.of("en", "Rare", "de", "Selten")),
                0x38BDF8, 1));
        placeholders = new CosmeticPlaceholders(cosmetics, (viewer, text) -> text.template(null));
    }

    @Test
    void countsWhatAPlayerOwns() {
        cosmetics.unlock(player, "flame-trail", CosmeticSource.PURCHASE);
        cosmetics.permitted.add("crown");

        assertEquals("2", placeholders.resolve(player, "cosmetics_owned"));
        assertEquals("3", placeholders.resolve(player, "cosmetics_total"));
        assertEquals("66", placeholders.resolve(player, "cosmetics_percent"));
        assertEquals("0", placeholders.resolve(null, "cosmetics_owned"));
    }

    @Test
    void showsTheCosmeticOfASlot() {
        cosmetics.unlock(player, "kill-hearts", CosmeticSource.REWARD);
        cosmetics.equip(player, "kill-hearts");

        assertEquals("Hearts", placeholders.resolve(player, "cosmetic_kill-effect"));
        assertEquals("kill-hearts", placeholders.resolve(player, "cosmetic_kill-effect_id"));
        assertEquals("Rare", placeholders.resolve(player, "cosmetic_kill-effect_rarity"));
        assertEquals("", placeholders.resolve(player, "cosmetic_trail"));
        assertEquals("", placeholders.resolve(player, "cosmetic_pet_id"));
        assertEquals("", placeholders.resolve(null, "cosmetic_trail"));
    }

    @Test
    void knowsWhetherOthersAreHidden() {
        assertEquals("false", placeholders.resolve(player, "cosmetics_hidden"));
        cosmetics.hideOthers(player, true);
        assertEquals("true", placeholders.resolve(player, "cosmetics_hidden"));
    }

    @Test
    void unknownPartsStayUnresolved() {
        assertNull(placeholders.resolve(player, "cosmetics_nonsense"));
        assertNull(placeholders.resolve(player, "cosmetic_Bad_Slot"));
        assertTrue(CosmeticPlaceholders.candidate("cosmetic_trail"));
        assertTrue(CosmeticPlaceholders.candidate("cosmetics_owned"));
        assertFalse(CosmeticPlaceholders.candidate("stat_wins"));
    }
}
