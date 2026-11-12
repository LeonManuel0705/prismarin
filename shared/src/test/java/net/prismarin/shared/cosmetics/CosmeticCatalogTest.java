package net.prismarin.shared.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticHat;
import net.prismarin.api.cosmetics.CosmeticParticles;
import net.prismarin.api.cosmetics.CosmeticRarity;
import net.prismarin.api.cosmetics.CosmeticSlot;
import net.prismarin.api.event.Subscription;
import org.junit.jupiter.api.Test;

class CosmeticCatalogTest {

    static final List<CosmeticRarity> RARITIES = List.of(CosmeticRarity.of("common", "Common", 0xD4D4D8, 0),
            CosmeticRarity.of("rare", "Rare", 0x38BDF8, 1), CosmeticRarity.of("legendary", "Legendary", 0xFBBF24, 2));

    static Cosmetic trail(String id) {
        return Cosmetic.particles(id, CosmeticSlot.TRAIL, null, CosmeticParticles.of("point", "flame"));
    }

    private static CosmeticCatalog catalog(List<Cosmetic> cosmetics, List<CosmeticCategory> categories) {
        CosmeticCatalog catalog = new CosmeticCatalog();
        catalog.load(cosmetics, categories, RARITIES);
        return catalog;
    }

    @Test
    void theMenuOrderFollowsRarityThenPrice() {
        CosmeticCatalog catalog = catalog(List.of(
                trail("gold").withRarity("legendary").withPrice("gems", 50),
                trail("blue").withRarity("rare").withPrice("coins", 900),
                trail("cheap").withPrice("coins", 100),
                trail("pricey").withPrice("coins", 400),
                trail("gift"),
                trail("pinned").withRarity("legendary").withOrder(-1)), List.of());

        assertEquals(List.of("pinned", "cheap", "pricey", "gift", "blue", "gold"),
                catalog.of(CosmeticSlot.TRAIL).stream().map(Cosmetic::id).toList());
    }

    @Test
    void pluginsAddCosmeticsButNeverReplaceConfiguredOnes() {
        CosmeticCatalog catalog = catalog(List.of(trail("flame")), List.of());

        Subscription star = catalog.register(trail("star"));

        assertThrows(IllegalArgumentException.class, () -> catalog.register(trail("flame")));
        assertThrows(IllegalArgumentException.class, () -> catalog.register(trail("star")));
        assertEquals(2, catalog.all().size());
        star.unsubscribe();
        assertTrue(catalog.find("star").isEmpty());
        assertTrue(catalog.find("flame").isPresent());
    }

    @Test
    void categoriesWithoutCosmeticsOrSwitchedOffStayOutOfTheMenu() {
        CosmeticCatalog catalog = catalog(List.of(trail("flame"),
                        Cosmetic.hat("crown", null, CosmeticHat.item("golden_helmet")),
                        Cosmetic.particles("ring", CosmeticSlot.AURA, null, CosmeticParticles.of("ring", "dust"))),
                List.of(new CosmeticCategory(CosmeticSlot.TRAIL, true, null, null, null, 2, List.of()),
                        new CosmeticCategory(CosmeticSlot.HAT, true, null, null, null, 1, List.of()),
                        new CosmeticCategory(CosmeticSlot.AURA, false, null, null, null, 0, List.of()),
                        new CosmeticCategory(CosmeticSlot.WIN_EFFECT, true, null, null, null, 0, List.of())));

        assertEquals(List.of(CosmeticSlot.HAT, CosmeticSlot.TRAIL),
                catalog.categories().stream().map(CosmeticCategory::slot).toList());
        assertFalse(catalog.enabled(CosmeticSlot.AURA));
        assertEquals(List.of("crown", "flame"), catalog.shown().stream().map(Cosmetic::id).sorted().toList());
    }

    @Test
    void slotsOfPluginsGetADefaultCategory() {
        CosmeticCatalog catalog = catalog(List.of(), List.of());
        CosmeticSlot pets = CosmeticSlot.of("pet");

        catalog.register(Cosmetic.particles("puppy", pets, "Puppy", CosmeticParticles.of("orbit", "heart")));

        CosmeticCategory category = catalog.category(pets);
        assertTrue(category.enabled());
        assertEquals(List.of(CosmeticCategory.EVERYWHERE), category.groups());
        assertEquals(List.of(pets), catalog.categories().stream().map(CosmeticCategory::slot).toList());
    }

    @Test
    void unknownRaritiesFallBackToTheFirstOne() {
        CosmeticCatalog catalog = catalog(List.of(trail("odd").withRarity("mythic")), List.of());

        assertEquals("common", catalog.rarityOf(catalog.find("odd").orElseThrow()).id());
        assertEquals(List.of("common", "rare", "legendary"), catalog.rarities().stream().map(CosmeticRarity::id)
                .toList());
    }
}
