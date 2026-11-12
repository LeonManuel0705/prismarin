package net.prismarin.shared.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.prismarin.api.cosmetics.CosmeticSlot;
import org.junit.jupiter.api.Test;

class ActiveSlotsTest {

    private static CosmeticCategory category(CosmeticSlot slot, boolean enabled, List<String> groups) {
        return new CosmeticCategory(slot, enabled, null, null, null, 0, groups);
    }

    @Test
    void byDefaultOnlyTheLobbyGroupShowsTrailsAndHats() {
        ActiveSlots lobby = new ActiveSlots("lobby-1", "lobby", List.of("lobby"));
        ActiveSlots game = new ActiveSlots("bedwars-1", "bedwars", List.of("lobby"));
        CosmeticCategory trail = category(CosmeticSlot.TRAIL, true, List.of());

        assertTrue(lobby.active(trail));
        assertFalse(game.active(trail));
    }

    @Test
    void aCategoryCanNameItsOwnGroupsServersOrEverywhere() {
        ActiveSlots game = new ActiveSlots("bedwars-1", "BedWars", List.of("lobby"));

        assertTrue(game.active(category(CosmeticSlot.HAT, true, List.of("bedwars"))));
        assertTrue(game.active(category(CosmeticSlot.HAT, true, List.of("BEDWARS-1"))));
        assertTrue(game.active(category(CosmeticSlot.KILL_EFFECT, true, List.of(CosmeticCategory.EVERYWHERE))));
        assertFalse(game.active(category(CosmeticSlot.HAT, true, List.of("skywars"))));
    }

    @Test
    void aSwitchedOffCategoryIsNeverActiveEvenWhenAPluginAsks() {
        ActiveSlots lobby = new ActiveSlots("lobby-1", "lobby", List.of("lobby"));
        CosmeticCategory off = category(CosmeticSlot.AURA, false, List.of(CosmeticCategory.EVERYWHERE));

        lobby.set(CosmeticSlot.AURA, true);

        assertFalse(lobby.active(off));
    }

    @Test
    void gamePluginsSwitchACategoryForARoundAndBack() {
        ActiveSlots game = new ActiveSlots("bedwars-1", "bedwars", List.of("lobby"));
        CosmeticCategory trail = category(CosmeticSlot.TRAIL, true, List.of());
        CosmeticCategory kill = category(CosmeticSlot.KILL_EFFECT, true, List.of(CosmeticCategory.EVERYWHERE));

        game.set(CosmeticSlot.TRAIL, true);
        game.set(CosmeticSlot.KILL_EFFECT, false);

        assertTrue(game.active(trail));
        assertFalse(game.active(kill));
        assertEquals(Optional.of(true), game.override(CosmeticSlot.TRAIL));

        game.reset(CosmeticSlot.TRAIL);
        game.reset(CosmeticSlot.KILL_EFFECT);

        assertFalse(game.active(trail));
        assertTrue(game.active(kill));
        assertEquals(Optional.empty(), game.override(CosmeticSlot.TRAIL));
    }

    @Test
    void reloadedDefaultGroupsApplyRightAway() {
        ActiveSlots game = new ActiveSlots("bedwars-1", "bedwars", List.of("lobby"));
        CosmeticCategory trail = category(CosmeticSlot.TRAIL, true, List.of());

        game.defaults(List.of("lobby", " BedWars "));

        assertTrue(game.active(trail));
        game.defaults(List.of());
        assertFalse(game.active(trail));
    }
}
