package net.prismarin.core.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticHat;
import net.prismarin.api.cosmetics.CosmeticParticles;
import net.prismarin.api.cosmetics.CosmeticPrice;
import net.prismarin.api.cosmetics.CosmeticRarity;
import net.prismarin.api.cosmetics.CosmeticSlot;
import net.prismarin.shared.cosmetics.CosmeticCategory;
import net.prismarin.shared.cosmetics.CosmeticShapes;
import net.prismarin.shared.cosmetics.HatSafety;
import net.prismarin.shared.cosmetics.HatTextures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class CosmeticSettingsTest {

    private static final List<String> CURRENCIES = List.of("coins", "gems");

    @TempDir
    Path directory;

    private CosmeticSettings load(String yaml) throws IOException {
        Path file = directory.resolve(CosmeticSettings.FILE_NAME);
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        return CosmeticSettings.load(file, CURRENCIES, true);
    }

    private static Cosmetic find(CosmeticSettings settings, String id) {
        return settings.cosmetics().stream().filter(cosmetic -> cosmetic.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void theDefaultCatalogueCoversEveryCategory() throws IOException {
        Path file = directory.resolve(CosmeticSettings.FILE_NAME);

        CosmeticSettings settings = CosmeticSettings.load(file, CURRENCIES, true);

        assertTrue(settings.problems().isEmpty(), settings.problems().toString());
        assertEquals(81, settings.cosmetics().size());
        assertEquals(CosmeticSettings.Triggers.DEFAULT, settings.triggers());
        assertEquals(List.of("lobby"), settings.activeGroups());
        assertFalse(settings.profileEntry());
        assertTrue(settings.equipAfterPurchase());
        assertEquals(5, settings.previewSeconds());
        assertEquals(CosmeticSettings.Performance.DEFAULT, settings.performance());
        assertEquals(List.of("common", "uncommon", "rare", "epic", "legendary"),
                settings.rarities().stream().map(CosmeticRarity::id).toList());
        assertEquals(CosmeticSlot.BUILT_IN, settings.categories().stream().map(CosmeticCategory::slot).toList());
        Map<CosmeticSlot, Long> perSlot = settings.cosmetics().stream()
                .collect(Collectors.groupingBy(Cosmetic::slot, Collectors.counting()));
        assertEquals(Set.copyOf(CosmeticSlot.BUILT_IN), perSlot.keySet());
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.contains("# Cosmetics: particle trails and auras"));
        assertTrue(written.contains("# One category per slot"));
        assertEquals(settings, CosmeticSettings.load(file, CURRENCIES, true));
        assertEquals(written, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void theDefaultsSellForCoinsAndGemsAndKeepSomeForRanks() {
        CosmeticSettings settings = CosmeticSettings.defaults();

        List<Cosmetic> permissionOnly = settings.cosmetics().stream().filter(Cosmetic::permissionOnly).toList();
        assertEquals(4, permissionOnly.size());
        assertTrue(permissionOnly.stream()
                .allMatch(cosmetic -> cosmetic.permission().startsWith("prismarin.cosmetics.")));
        Set<String> currencies = settings.cosmetics().stream().flatMap(cosmetic -> cosmetic.priceTag().stream())
                .map(CosmeticPrice::currency).collect(Collectors.toSet());
        assertEquals(Set.of("coins", "gems"), currencies);
        assertTrue(settings.cosmetics().stream().filter(cosmetic -> cosmetic.priceTag().map(CosmeticPrice::currency)
                .filter("gems"::equals).isPresent()).count() >= 5);
        for (Cosmetic cosmetic : settings.cosmetics()) {
            assertTrue(cosmetic.name().templates().containsKey("en"), cosmetic.id());
            assertTrue(cosmetic.name().templates().containsKey("de"), cosmetic.id());
            assertTrue(cosmetic.about().isPresent(), cosmetic.id());
            cosmetic.particles().ifPresent(look -> assertTrue(CosmeticShapes.BUILT_IN.contains(look.shape()),
                    cosmetic.id()));
            cosmetic.hat().filter(CosmeticHat::head).ifPresent(hat -> assertTrue(HatTextures.hash(hat.texture())
                    .isPresent(), cosmetic.id()));
        }
        CosmeticParticles wings = find(settings, "angel-wings").particles().orElseThrow();
        assertEquals(CosmeticShapes.WINGS, wings.shape());
        assertEquals(List.of(0xFDE68A, 0xFFFFFF), wings.colors());
        assertEquals("ui.toast.challenge_complete", find(settings, "golden-pillar").particles().orElseThrow()
                .sound());
        assertEquals(CosmeticHat.item("glass"), find(settings, "astronaut").look());
    }

    @Test
    void ownCosmeticsAndCategoriesAreRead() throws IOException {
        CosmeticSettings settings = load("""
                catalogue-version: 2
                active-groups: [lobby, hub-2]
                preview-seconds: 99
                sub-commands:
                  hide: false
                lobby:
                  profile-entry: true
                performance:
                  interval-ticks: 0
                  max-particles: 400
                rarities:
                  basic:
                    name: Basic
                    color: "#123456"
                categories:
                  trail:
                    groups: ["*"]
                  hat:
                    enabled: false
                  pet:
                    icon: bone
                    order: 9
                cosmetics:
                  comet:
                    category: trail
                    rarity: basic
                    price: 70
                    currency: GEMS
                    effect:
                      particle: minecraft:END_ROD
                      colors: ["#FF0000", "&#00FF00"]
                      count: 3
                  puppy:
                    category: pet
                    rarity: basic
                    name:
                      en: Puppy
                      de: Welpe
                    effect:
                      particle: heart
                  cap:
                    category: hat
                    rarity: basic
                    item: leather_helmet
                    permission: shop.cap
                """);

        assertTrue(settings.problems().isEmpty(), settings.problems().toString());
        assertEquals(List.of("lobby", "hub-2"), settings.activeGroups());
        assertEquals(30, settings.previewSeconds());
        assertFalse(settings.hideCommand());
        assertTrue(settings.profileEntry());
        assertEquals(new CosmeticSettings.Performance(1, 400, CosmeticSettings.DEFAULT_VIEW_DISTANCE,
                CosmeticSettings.DEFAULT_MAX_EFFECTS, CosmeticSettings.DEFAULT_MAX_EFFECT_ENTITIES),
                settings.performance());
        assertEquals(3, settings.cosmetics().size());
        Cosmetic comet = find(settings, "comet");
        assertEquals(CosmeticPrice.of("gems", 70), comet.price());
        assertEquals("basic", comet.rarity());
        CosmeticParticles look = comet.particles().orElseThrow();
        assertEquals("end_rod", look.particle());
        assertEquals(CosmeticShapes.POINT, look.shape());
        assertEquals(List.of(0xFF0000, 0x00FF00), look.colors());
        assertEquals(3, look.count());
        assertEquals(CosmeticShapes.POINT, find(settings, "puppy").particles().orElseThrow().shape());
        assertEquals("Welpe", find(settings, "puppy").name().template(Locale.GERMAN));
        assertTrue(find(settings, "cap").permissionOnly());
        Map<CosmeticSlot, CosmeticCategory> categories = settings.categories().stream()
                .collect(Collectors.toMap(CosmeticCategory::slot, category -> category));
        assertEquals(List.of(CosmeticCategory.EVERYWHERE), categories.get(CosmeticSlot.TRAIL).groups());
        assertFalse(categories.get(CosmeticSlot.HAT).enabled());
        assertEquals("bone", categories.get(CosmeticSlot.of("pet")).icon());
        assertEquals(9, categories.get(CosmeticSlot.of("pet")).order());
        assertEquals(List.of(CosmeticCategory.EVERYWHERE), categories.get(CosmeticSlot.KILL_EFFECT).groups());
        assertEquals(List.of(), categories.get(CosmeticSlot.AURA).groups());
    }

    @Test
    void mistakesAreReportedAndTheRestStillLoads() throws IOException {
        CosmeticSettings settings = load("""
                catalogue-version: 2
                rarities:
                  common:
                    color: purple
                categories:
                  Bad_Slot:
                    enabled: true
                cosmetics:
                  Bad_Id:
                    category: trail
                  no-category:
                    effect:
                      particle: flame
                  odd-rarity:
                    category: trail
                    rarity: mythic
                    effect:
                      particle: flame
                  tokens:
                    category: trail
                    price: 10
                    currency: tokens
                    effect:
                      particle: flame
                  negative:
                    category: aura
                    price: -5
                    effect:
                      particle: dust
                      colors: ["#GGGGGG", "#FFFFFF"]
                  no-particle:
                    category: aura
                  no-item:
                    category: hat
                  bad-texture:
                    category: hat
                    texture: "not a head"
                  good:
                    category: hat
                    item: carved_pumpkin
                """);

        List<String> problems = settings.problems();
        assertEquals(Set.of("negative", "odd-rarity", "tokens", "good"),
                settings.cosmetics().stream().map(Cosmetic::id).collect(Collectors.toSet()));
        assertTrue(contains(problems, "rarities.common.color"));
        assertTrue(contains(problems, "categories.Bad_Slot"));
        assertTrue(contains(problems, "cosmetics.Bad_Id"));
        assertTrue(contains(problems, "cosmetics.no-category.category"));
        assertTrue(contains(problems, "cosmetics.odd-rarity.rarity 'mythic'"));
        assertTrue(contains(problems, "cosmetics.tokens.currency 'tokens'"));
        assertTrue(contains(problems, "cosmetics.negative.price"));
        assertTrue(contains(problems, "cosmetics.negative.effect.colors: '#GGGGGG'"));
        assertTrue(contains(problems, "cosmetics.no-particle.effect.particle"));
        assertTrue(contains(problems, "cosmetics.no-item needs an item or a texture"));
        assertTrue(contains(problems, "cosmetics.bad-texture.texture"));
        assertEquals("common", find(settings, "odd-rarity").rarity());
        assertFalse(find(settings, "tokens").purchasable());
        assertEquals(List.of(0xFFFFFF), find(settings, "negative").particles().orElseThrow().colors());
    }

    @Test
    void withoutEconomyPricesAreKeptButNotChecked() throws IOException {
        Path file = directory.resolve(CosmeticSettings.FILE_NAME);
        Files.writeString(file, """
                catalogue-version: 2
                cosmetics:
                  tokens:
                    category: trail
                    price: 10
                    currency: tokens
                    effect:
                      particle: flame
                """, StandardCharsets.UTF_8);

        CosmeticSettings settings = CosmeticSettings.load(file, List.of(), false);

        assertTrue(settings.problems().isEmpty(), settings.problems().toString());
        assertEquals(CosmeticPrice.of("tokens", 10), find(settings, "tokens").price());
    }

    @Test
    void aBrokenFileFallsBackToTheDefaults() throws IOException {
        CosmeticSettings settings = load("cosmetics: [unclosed");

        assertEquals(1, settings.problems().size());
        assertEquals(CosmeticSettings.defaults().cosmetics(), settings.cosmetics());
    }

    @Test
    void anOlderFileGetsTheNewDefaultsOnceWithoutLosingChanges() throws IOException {
        Path file = directory.resolve(CosmeticSettings.FILE_NAME);
        Files.writeString(file, """
                cosmetics:
                  heart-burst:
                    category: kill-effect
                    rarity: legendary
                    price: 9
                    currency: gems
                    effect:
                      particle: heart
                  flame-arrows:
                    category: projectile-trail
                    name: Mine
                    effect:
                      particle: end_rod
                  own-hat:
                    category: hat
                    item: cake
                """, StandardCharsets.UTF_8);

        CosmeticSettings first = CosmeticSettings.load(file, CURRENCIES, true);

        assertTrue(first.problems().isEmpty(), first.problems().toString());
        Set<String> ids = first.cosmetics().stream().map(Cosmetic::id).collect(Collectors.toSet());
        assertTrue(ids.containsAll(Set.of("squid-launch", "fireworks-show", "dragon-head", "rain-cloud",
                "comet-arrows", "own-hat", "heart-burst")));
        assertFalse(ids.contains("flame-trail"));
        assertFalse(ids.contains("golden-pillar"));
        assertEquals(3 + 56 - 1, first.cosmetics().size());
        assertEquals(CosmeticPrice.of("gems", 9), find(first, "heart-burst").price());
        assertEquals("legendary", find(first, "heart-burst").rarity());
        assertEquals("end_rod", find(first, "flame-arrows").particles().orElseThrow().particle());
        assertEquals("Mine", find(first, "flame-arrows").name().template(null));
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.contains("catalogue-version: " + CosmeticSettings.CATALOGUE_VERSION));
        assertTrue(written.contains("triggers:"));

        ConfigurationNode root = YamlConfigurationLoader.builder().path(file).build().load();
        root.node("cosmetics").removeChild("squid-launch");
        root.node("cosmetics").removeChild("dragon-head");
        YamlConfigurationLoader.builder().path(file).nodeStyle(NodeStyle.BLOCK).indent(2).build().save(root);

        CosmeticSettings second = CosmeticSettings.load(file, CURRENCIES, true);

        Set<String> again = second.cosmetics().stream().map(Cosmetic::id).collect(Collectors.toSet());
        assertFalse(again.contains("squid-launch"));
        assertFalse(again.contains("dragon-head"));
        assertEquals(first.cosmetics().size() - 2, second.cosmetics().size());
        String settled = Files.readString(file, StandardCharsets.UTF_8);
        CosmeticSettings.load(file, CURRENCIES, true);
        assertEquals(settled, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void aNewerCatalogueVersionIsLeftAlone() throws IOException {
        CosmeticSettings settings = load("""
                catalogue-version: 7
                cosmetics:
                  only-one:
                    category: trail
                    effect:
                      particle: flame
                """);

        assertEquals(List.of("only-one"), settings.cosmetics().stream().map(Cosmetic::id).toList());
    }

    @Test
    void triggersAndEffectFieldsAreRead() throws IOException {
        CosmeticSettings settings = load("""
                catalogue-version: 2
                performance:
                  max-effects: 0
                  max-effect-entities: 50
                triggers:
                  projectile-trail:
                    auto: false
                    projectiles: [ARROW, "minecraft:fishing_bobber"]
                    interval-ticks: 99
                    max-ticks: 5
                    max-tracked: 12
                  kill-effect:
                    auto: false
                    mob-kills: true
                cosmetics:
                  boom:
                    category: kill-effect
                    effect:
                      particle: block
                      kind: Squid
                      duration: 999
                      variant: minecraft:Redstone_Block
                """);

        assertTrue(settings.problems().isEmpty(), settings.problems().toString());
        CosmeticSettings.Triggers triggers = settings.triggers();
        assertFalse(triggers.projectileAuto());
        assertEquals(List.of("arrow", "fishing_bobber"), triggers.projectiles());
        assertTrue(triggers.tracks("fishing_bobber"));
        assertFalse(triggers.tracks("snowball"));
        assertEquals(10, triggers.trailInterval());
        assertEquals(20, triggers.trailTicks());
        assertEquals(12, triggers.trailTracked());
        assertFalse(triggers.killAuto());
        assertTrue(triggers.mobKills());
        assertFalse(triggers.auto(CosmeticSlot.KILL_EFFECT));
        assertFalse(triggers.auto(CosmeticSlot.WIN_EFFECT));
        assertEquals(1, settings.performance().maxEffects());
        assertEquals(50, settings.performance().maxEffectEntities());
        CosmeticParticles look = find(settings, "boom").particles().orElseThrow();
        assertEquals("squid", look.kind());
        assertEquals(CosmeticParticles.MAX_DURATION, look.duration());
        assertEquals("redstone_block", look.variant());
        assertEquals(CosmeticShapes.BURST, look.shape());
        assertTrue(CosmeticSettings.Triggers.DEFAULT.tracks("arrow"));
        assertFalse(CosmeticSettings.Triggers.DEFAULT.tracks("fishing_bobber"));
        assertTrue(CosmeticSettings.Triggers.DEFAULT.auto(CosmeticSlot.PROJECTILE_TRAIL));
    }

    @Test
    void hatsThatWouldChangeTheGameAreRefusedOrReplaced() throws IOException {
        CosmeticSettings settings = load("""
                catalogue-version: 2
                cosmetics:
                  turtle:
                    category: hat
                    item: turtle_helmet
                  pumpkin:
                    category: hat
                    item: carved_pumpkin
                  zombie:
                    category: hat
                    item: zombie_head
                  crown:
                    category: hat
                    item: golden_helmet
                """);

        Set<String> ids = settings.cosmetics().stream().map(Cosmetic::id).collect(Collectors.toSet());
        assertEquals(Set.of("pumpkin", "zombie", "crown"), ids);
        assertEquals(CosmeticHat.item("jack_o_lantern"), find(settings, "pumpkin").look());
        assertEquals(CosmeticHat.item("golden_helmet"), find(settings, "crown").look());
        assertTrue(contains(settings.problems(), "cosmetics.turtle.item: turtle_helmet gives water breathing"));
        assertTrue(contains(settings.problems(), "cosmetics.pumpkin.item: carved_pumpkin blurs"));
        assertTrue(contains(settings.problems(), "cosmetics.zombie.item: zombie_head makes zombies"));
        assertEquals(3, settings.problems().size());
    }

    @Test
    void theNewDefaultsAreVariedAndSafe() {
        CosmeticSettings settings = CosmeticSettings.defaults();
        Map<CosmeticSlot, List<Cosmetic>> bySlot = settings.cosmetics().stream()
                .collect(Collectors.groupingBy(Cosmetic::slot));

        assertEquals(15, bySlot.get(CosmeticSlot.PROJECTILE_TRAIL).size());
        assertEquals(16, bySlot.get(CosmeticSlot.KILL_EFFECT).size());
        assertEquals(10, bySlot.get(CosmeticSlot.WIN_EFFECT).size());
        assertEquals(20, bySlot.get(CosmeticSlot.HAT).size());
        assertEquals(13, bySlot.get(CosmeticSlot.AURA).size());
        for (CosmeticSlot slot : List.of(CosmeticSlot.PROJECTILE_TRAIL, CosmeticSlot.KILL_EFFECT,
                CosmeticSlot.WIN_EFFECT, CosmeticSlot.HAT, CosmeticSlot.AURA)) {
            Set<String> rarities = bySlot.get(slot).stream().map(Cosmetic::rarity).collect(Collectors.toSet());
            assertTrue(rarities.size() >= 4, slot + " " + rarities);
        }
        Set<String> kinds = settings.cosmetics().stream().flatMap(cosmetic -> cosmetic.particles().stream())
                .map(CosmeticParticles::kind).collect(Collectors.toSet());
        assertEquals(Set.of("particles", "lightning", "lightning-storm", "squid", "fountain", "tombstone",
                "firework", "fireworks", "totem", "sheep"), kinds);
        for (Cosmetic cosmetic : settings.cosmetics()) {
            cosmetic.hat().ifPresent(hat -> assertTrue(HatSafety.check(hat.material()).reason().isEmpty(),
                    cosmetic.id()));
            cosmetic.particles().filter(look -> !look.kind().equals("particles"))
                    .ifPresent(look -> assertTrue(cosmetic.slot().effect(), cosmetic.id()));
            assertTrue(cosmetic.icon().isEmpty() || cosmetic.icon().matches("[a-z0-9_]+"), cosmetic.id());
            assertFalse(cosmetic.about().orElseThrow().templates().get("de").contains("\u2014"), cosmetic.id());
        }
        assertTrue(bySlot.get(CosmeticSlot.HAT).stream().anyMatch(Cosmetic::permissionOnly));
        assertTrue(bySlot.get(CosmeticSlot.HAT).stream().filter(cosmetic -> !cosmetic.purchasable()
                && !cosmetic.permissionOnly()).count() >= 2);
    }

    private static boolean contains(List<String> problems, String start) {
        return problems.stream().anyMatch(problem -> problem.startsWith(start));
    }
}
