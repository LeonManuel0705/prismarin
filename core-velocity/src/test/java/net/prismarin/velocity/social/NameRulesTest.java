package net.prismarin.velocity.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class NameRulesTest {

    @Test
    void strangersOnlySeeTheNick() {
        assertEquals("Shadow", NameRules.visible(false, false, false, "Leon", Optional.of("Shadow")));
        assertTrue(NameRules.masked(false, false, false, Optional.of("Shadow")));
    }

    @Test
    void selfFriendsAndStaffSeeTheRealName() {
        assertEquals("Leon", NameRules.visible(true, false, false, "Leon", Optional.of("Shadow")));
        assertEquals("Leon", NameRules.visible(false, true, false, "Leon", Optional.of("Shadow")));
        assertEquals("Leon", NameRules.visible(false, false, true, "Leon", Optional.of("Shadow")));
        assertFalse(NameRules.masked(false, true, false, Optional.of("Shadow")));
    }

    @Test
    void withoutNickEveryoneSeesTheRealName() {
        assertEquals("Leon", NameRules.visible(false, false, false, "Leon", Optional.empty()));
        assertEquals("Leon", NameRules.visible(false, false, false, "Leon", Optional.of(" ")));
        assertFalse(NameRules.masked(false, false, false, Optional.empty()));
    }
}
