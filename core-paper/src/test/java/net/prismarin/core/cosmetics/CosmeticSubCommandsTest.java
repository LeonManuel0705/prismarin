package net.prismarin.core.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class CosmeticSubCommandsTest {

    private static final Predicate<String> PLAYER = Set.of(CosmeticSubCommands.USE_PERMISSION)::contains;
    private static final Predicate<String> STAFF = Set.of(CosmeticSubCommands.USE_PERMISSION,
            CosmeticSubCommands.GIVE_PERMISSION, CosmeticSubCommands.TAKE_PERMISSION)::contains;
    private static final Predicate<String> NOBODY = permission -> false;
    private static final List<String> CATEGORIES = List.of("trail", "aura", "hat");

    @Test
    void playersSeeTheMenuPartsOnly() {
        assertEquals(List.of("hide", "help"), CosmeticSubCommands.ALL.visible(PLAYER));
        assertEquals(List.of("trail", "aura", "hat", "hide", "help"),
                CosmeticSubCommands.ALL.suggestions(PLAYER, "", CATEGORIES));
        assertFalse(CosmeticSubCommands.ALL.allowed(CosmeticSubCommands.GIVE, PLAYER));
        assertFalse(CosmeticSubCommands.ALL.suggestions(PLAYER, "g", CATEGORIES).contains("give"));
    }

    @Test
    void staffAlsoGetsGiveAndTake() {
        assertEquals(List.of("hide", "give", "take", "help"), CosmeticSubCommands.ALL.visible(STAFF));
        assertEquals(List.of("give"), CosmeticSubCommands.ALL.suggestions(STAFF, "G", CATEGORIES));
        assertEquals(List.of("trail", "take"), CosmeticSubCommands.ALL.suggestions(STAFF, "t", CATEGORIES));
    }

    @Test
    void aSwitchedOffHideDisappearsEverywhere() {
        CosmeticSubCommands off = new CosmeticSubCommands(false);

        assertEquals(List.of("give", "take", "help"), off.visible(STAFF));
        assertEquals(List.of("help"), off.visible(PLAYER));
        assertEquals(List.of("hat", "help"), off.suggestions(PLAYER, "h", CATEGORIES));
        assertFalse(off.allowed(CosmeticSubCommands.HIDE, STAFF));
    }

    @Test
    void withoutTheUsePermissionNoCategoryIsOffered() {
        assertFalse(CosmeticSubCommands.ALL.opens(NOBODY));
        assertEquals(List.of("help"), CosmeticSubCommands.ALL.suggestions(NOBODY, "", CATEGORIES));
        assertTrue(CosmeticSubCommands.ALL.opens(PLAYER));
        assertFalse(CosmeticSubCommands.ALL.allowed("nonsense", STAFF));
        assertEquals("hide", CosmeticSubCommands.action("HIDE"));
    }
}
