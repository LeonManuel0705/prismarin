package net.prismarin.lobby.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.prismarin.api.social.PartyAction;
import net.prismarin.api.social.PartyRole;
import net.prismarin.api.social.PartyRules;
import net.prismarin.api.social.PartySettings;
import org.junit.jupiter.api.Test;

class SocialLayoutTest {

    @Test
    void contentFillsFourInnerRows() {
        List<Integer> slots = SocialLayout.contentSlots(SocialLayout.PER_PAGE);
        assertEquals(28, slots.size());
        assertEquals(10, slots.getFirst());
        assertEquals(43, slots.getLast());
        for (int slot : slots) {
            int column = slot % 9;
            assertTrue(column >= 1 && column <= 7, "slot " + slot);
        }
        assertEquals(28, new HashSet<>(slots).size());
    }

    @Test
    void contentNeverReachesTheControls() {
        Set<Integer> controls = Set.of(SocialLayout.INFO, SocialLayout.BACK, SocialLayout.REQUESTS,
                SocialLayout.PREVIOUS, SocialLayout.PARTY, SocialLayout.NEXT, SocialLayout.SETTINGS,
                SocialLayout.CLOSE);
        for (int slot : SocialLayout.contentSlots(100)) {
            assertFalse(controls.contains(slot), "slot " + slot);
        }
        assertEquals(28, SocialLayout.contentSlots(100).size());
        assertEquals(List.of(10, 11, 12), SocialLayout.contentSlots(3));
        assertEquals(List.of(37, 38), SocialLayout.slots(4, 1, 2));
    }

    @Test
    void pagingButtonsShowOnlyWhenUseful() {
        assertFalse(SocialLayout.hasPrevious(0));
        assertTrue(SocialLayout.hasPrevious(1));
        assertTrue(SocialLayout.hasNext(0, 2));
        assertFalse(SocialLayout.hasNext(1, 2));
        assertFalse(SocialLayout.hasNext(0, 1));
    }

    @Test
    void memberButtonsOnlyAppearForAllowedRoles() {
        assertEquals(List.of(SocialLayout.MemberAction.PROMOTE, SocialLayout.MemberAction.TRANSFER,
                SocialLayout.MemberAction.KICK), actions(PartyRole.LEADER, PartyRole.MEMBER, false));
        assertEquals(List.of(SocialLayout.MemberAction.DEMOTE, SocialLayout.MemberAction.TRANSFER,
                SocialLayout.MemberAction.KICK), actions(PartyRole.LEADER, PartyRole.MODERATOR, false));
        assertEquals(List.of(SocialLayout.MemberAction.KICK), actions(PartyRole.MODERATOR, PartyRole.MEMBER, false));
        assertEquals(List.of(), actions(PartyRole.MODERATOR, PartyRole.MODERATOR, false));
        assertEquals(List.of(), actions(PartyRole.MODERATOR, PartyRole.LEADER, false));
        assertEquals(List.of(SocialLayout.MemberAction.BEFRIEND), actions(PartyRole.MEMBER, PartyRole.LEADER, true));
        assertEquals(List.of(), actions(PartyRole.MEMBER, PartyRole.MEMBER, false));
        assertFalse(isContentSlot(SocialLayout.PARTY_OPTIONS));
    }

    private static List<SocialLayout.MemberAction> actions(PartyRole viewer, PartyRole target, boolean befriend) {
        PartySettings settings = PartySettings.DEFAULT;
        return SocialLayout.memberActions(PartyRules.allowed(PartyAction.PROMOTE, viewer, target, settings),
                PartyRules.allowed(PartyAction.DEMOTE, viewer, target, settings),
                PartyRules.allowed(PartyAction.TRANSFER, viewer, target, settings), befriend,
                PartyRules.allowed(PartyAction.KICK, viewer, target, settings));
    }

    private static boolean isContentSlot(int slot) {
        return SocialLayout.contentSlots(SocialLayout.PER_PAGE).contains(slot);
    }

    @Test
    void badgesStayWithinAStack() {
        assertEquals(1, SocialLayout.badge(0));
        assertEquals(7, SocialLayout.badge(7));
        assertEquals(64, SocialLayout.badge(500));
    }
}
