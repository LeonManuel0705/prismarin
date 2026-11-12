package net.prismarin.api.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CosmeticModelTest {

    @Test
    void slotsAreLowerCaseIdsWithoutUnderscores() {
        assertEquals(CosmeticSlot.KILL_EFFECT, CosmeticSlot.of(" Kill-Effect "));
        assertEquals(Optional.empty(), CosmeticSlot.parse("kill_effect"));
        assertEquals(Optional.of(CosmeticSlot.of("pet")), CosmeticSlot.parse("PET"));
        assertThrows(IllegalArgumentException.class, () -> CosmeticSlot.of(""));
        assertTrue(CosmeticSlot.TRAIL.continuous());
        assertTrue(CosmeticSlot.HAT.hat());
        assertTrue(CosmeticSlot.WIN_EFFECT.effect());
        assertTrue(CosmeticSlot.of("pet").effect());
        assertFalse(CosmeticSlot.of("pet").builtIn());
    }

    @Test
    void theLookHasToFitTheSlot() {
        CosmeticParticles flame = CosmeticParticles.of("point", "minecraft:flame");

        assertThrows(IllegalArgumentException.class, () -> Cosmetic.particles("flame", CosmeticSlot.HAT, null,
                flame));
        assertThrows(IllegalArgumentException.class, () -> new Cosmetic("crown", CosmeticSlot.AURA, null, null, null,
                null, null, null, false, 0, CosmeticHat.item("golden_helmet")));
        Cosmetic trail = Cosmetic.particles("flame-trail", CosmeticSlot.TRAIL, null, flame);
        assertEquals("flame", trail.particles().orElseThrow().particle());
        assertEquals("Flame trail", trail.name().template(null));
        assertEquals("common", trail.rarity());
        assertTrue(trail.hat().isEmpty());
    }

    @Test
    void pricesPermissionsAndRewardsDecideHowACosmeticIsGot() {
        Cosmetic base = Cosmetic.hat("crown", "Crown", CosmeticHat.item("GOLDEN_HELMET"));

        assertTrue(base.withPrice("Gems", 80).purchasable());
        assertEquals(CosmeticPrice.of("gems", 80), base.withPrice("gems", 80).price());
        assertTrue(base.withPermission("Prismarin.Cosmetics.VIP").permissionOnly());
        assertEquals("prismarin.cosmetics.vip", base.withPermission("Prismarin.Cosmetics.VIP").permission());
        assertFalse(base.purchasable());
        assertFalse(base.permissionOnly());
        assertThrows(IllegalArgumentException.class, () -> CosmeticPrice.of("coins", 0));
        assertThrows(IllegalArgumentException.class, () -> base.withPermission("no spaces allowed"));
        assertThrows(IllegalArgumentException.class, () -> Cosmetic.hat("Bad_Id", null, CosmeticHat.item("tnt")));
    }

    @Test
    void particleSettingsStayInSaneBounds() {
        CosmeticParticles look = CosmeticParticles.of("ring", "dust").withCount(500).withSize(-1f).withSpread(-2)
                .withPoints(9_999).withRadius(Double.NaN).withSpin(7.0).withColors(0x1FF0000, 0x00FF00);

        assertEquals(CosmeticParticles.MAX_COUNT, look.count());
        assertEquals(1f, look.size());
        assertEquals(0.0, look.spread());
        assertEquals(CosmeticParticles.MAX_POINTS, look.points());
        assertEquals(0.0, look.radius());
        assertEquals(2.0, look.spin());
        assertEquals(0xFF0000, look.color(0));
        assertEquals(0x00FF00, look.color(3));
        assertEquals(CosmeticParticles.NO_COLOR, CosmeticParticles.of("ring", "dust").color(0));
        assertThrows(IllegalArgumentException.class, () -> CosmeticParticles.of("ring", "no particle"));
    }

    @Test
    void effectKindsDurationsAndVariantsAreNormalized() {
        CosmeticParticles plain = CosmeticParticles.of("burst", "firework");
        CosmeticParticles squid = plain.withKind(" Squid ").withDuration(9_999).withVariant("minecraft:Ink_Sac");
        CosmeticParticles old = new CosmeticParticles("ring", "dust", null, 1f, 1, 0.0, 0.0, 0.0, 0.0, 0, 0.0, "");

        assertEquals(CosmeticParticles.PARTICLES, plain.kind());
        assertEquals(0, plain.duration());
        assertEquals("", plain.variant());
        assertFalse(plain.runs());
        assertEquals("squid", squid.kind());
        assertEquals(CosmeticParticles.MAX_DURATION, squid.duration());
        assertEquals("ink_sac", squid.variant());
        assertTrue(squid.runs());
        assertEquals("squid", squid.withColors(0xFFFFFF).withSpin(0.5).kind());
        assertEquals(CosmeticParticles.PARTICLES, old.kind());
        assertEquals(0, plain.withDuration(-4).duration());
        assertEquals(CosmeticParticles.PARTICLES, plain.withKind(" ").kind());
        assertThrows(IllegalArgumentException.class, () -> plain.withKind("no kind"));
        assertThrows(IllegalArgumentException.class, () -> plain.withVariant("no item"));
    }

    @Test
    void anEffectEventCarriesItsTrigger() {
        Cosmetic squid = Cosmetic.particles("squid-launch", CosmeticSlot.KILL_EFFECT, null,
                CosmeticParticles.of("burst", "squid_ink").withKind("squid"));
        CosmeticEffectEvent event = new CosmeticEffectEvent(UUID.randomUUID(), squid,
                CosmeticSpot.of("world", 1, 2, 3), CosmeticEffectEvent.Trigger.KILL);

        assertEquals(CosmeticSlot.KILL_EFFECT, event.slot());
        assertEquals(CosmeticEffectEvent.Trigger.KILL, event.trigger());
        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
        assertFalse(CosmeticApi.effectEntity(UUID.randomUUID()));
    }

    @Test
    void hatsNeedAnItemOrAHeadTexture() {
        assertThrows(IllegalArgumentException.class, () -> new CosmeticHat("", ""));
        assertEquals(CosmeticHat.PLAYER_HEAD, new CosmeticHat("", "abc").material());
        assertTrue(CosmeticHat.head("abc").head());
        assertEquals("glass", CosmeticHat.item("minecraft:GLASS").material());
    }

    @Test
    void stateAndResultsAnswerTheUsualQuestions() {
        UUID player = UUID.randomUUID();
        PlayerCosmetics state = new PlayerCosmetics(player,
                Map.of("flame", new OwnedCosmetic("flame", CosmeticSource.PURCHASE, 1L)),
                Map.of(CosmeticSlot.TRAIL, "flame"), true);

        assertTrue(state.owns("flame"));
        assertTrue(state.wears("flame"));
        assertEquals(Optional.of("flame"), state.equipped(CosmeticSlot.TRAIL));
        assertEquals(Optional.empty(), state.equipped(CosmeticSlot.HAT));
        assertFalse(PlayerCosmetics.none(player).owns("flame"));
        assertEquals(Optional.of(CosmeticSource.REWARD), CosmeticSource.byId("Reward"));
        assertEquals(30L, CosmeticResult.missing(null, 30L).missing());
        assertFalse(CosmeticResult.of(CosmeticResult.Status.NOT_OWNED).success());
        assertTrue(CosmeticResult.of(CosmeticResult.Status.DONE).success());
    }
}
