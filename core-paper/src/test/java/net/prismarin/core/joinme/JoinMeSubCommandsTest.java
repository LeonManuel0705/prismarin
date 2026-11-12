package net.prismarin.core.joinme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class JoinMeSubCommandsTest {

    private static final Predicate<String> PLAYER = Set.of(JoinMeSubCommands.USE_PERMISSION)::contains;
    private static final Predicate<String> CREATOR = Set.of(JoinMeSubCommands.USE_PERMISSION,
            JoinMeSubCommands.CREATE_PERMISSION)::contains;
    private static final Predicate<String> STAFF = Set.of(JoinMeSubCommands.USE_PERMISSION,
            JoinMeSubCommands.CANCEL_PERMISSION)::contains;

    @Test
    void theClickCommandIsNeverShown() {
        for (Predicate<String> sender : List.of(PLAYER, CREATOR, STAFF)) {
            assertFalse(JoinMeSubCommands.ALL.visible(sender).contains(JoinMeSubCommands.JOIN));
            assertFalse(JoinMeSubCommands.ALL.suggestions(sender, "").contains(JoinMeSubCommands.JOIN));
            assertEquals(List.of(), JoinMeSubCommands.ALL.suggestions(sender, "j"));
            assertTrue(JoinMeSubCommands.ALL.allowed(JoinMeSubCommands.JOIN, sender));
        }
        assertTrue(JoinMeSubCommands.hidden("JOIN"));
        assertFalse(JoinMeSubCommands.hidden("list"));
    }

    @Test
    void everybodySeesOnlyWhatHeMayUse() {
        assertEquals(List.of("list", "help"), JoinMeSubCommands.ALL.visible(PLAYER));
        assertEquals(List.of("create", "preview", "list", "cancel", "help"), JoinMeSubCommands.ALL.visible(CREATOR));
        assertEquals(List.of("list", "cancel", "help"), JoinMeSubCommands.ALL.visible(STAFF));
        assertEquals(List.of("preview", "list", "cancel", "help"), JoinMeSubCommands.ALL.suggestions(CREATOR, ""));
        assertEquals(List.of("cancel"), JoinMeSubCommands.ALL.suggestions(STAFF, "C"));
        assertFalse(JoinMeSubCommands.ALL.cancelsOthers(CREATOR));
        assertTrue(JoinMeSubCommands.ALL.cancelsOthers(STAFF));
        assertFalse(JoinMeSubCommands.ALL.allowed("unknown", STAFF));
    }

    @Test
    void switchedOffPartsDisappearEverywhere() {
        JoinMeSubCommands off = new JoinMeSubCommands(Set.of("LIST", "cancel", "create", "nonsense"));
        assertEquals(Set.of("list", "cancel"), off.off());
        assertEquals(List.of("create", "preview", "help"), off.visible(CREATOR));
        assertEquals(List.of("preview", "help"), off.suggestions(CREATOR, ""));
        assertFalse(off.allowed(JoinMeSubCommands.LIST, PLAYER));
        assertFalse(off.allowed(JoinMeSubCommands.CANCEL, STAFF));
        assertFalse(off.cancelsOthers(STAFF));
        assertTrue(off.allowed(JoinMeSubCommands.JOIN, PLAYER));
    }

    @Test
    void aBareJoinMeCreatesListsOrHelps() {
        assertEquals(JoinMeSubCommands.CREATE, JoinMeSubCommands.ALL.fallback(CREATOR));
        assertEquals(JoinMeSubCommands.LIST, JoinMeSubCommands.ALL.fallback(PLAYER));
        assertEquals(JoinMeSubCommands.HELP, new JoinMeSubCommands(Set.of("list")).fallback(PLAYER));
    }

    @Test
    void thePreviewIsOnlyThereForCreatorsAndWhenSwitchedOn() {
        assertTrue(JoinMeSubCommands.ALL.allowed(JoinMeSubCommands.PREVIEW, CREATOR));
        assertTrue(JoinMeSubCommands.ALL.suggestions(CREATOR, "pre").contains(JoinMeSubCommands.PREVIEW));
        for (Predicate<String> sender : List.of(PLAYER, STAFF)) {
            assertFalse(JoinMeSubCommands.ALL.allowed(JoinMeSubCommands.PREVIEW, sender));
            assertFalse(JoinMeSubCommands.ALL.visible(sender).contains(JoinMeSubCommands.PREVIEW));
            assertEquals(List.of(), JoinMeSubCommands.ALL.suggestions(sender, "p"));
        }
        JoinMeSubCommands off = new JoinMeSubCommands(Set.of("preview"));
        assertFalse(off.allowed(JoinMeSubCommands.PREVIEW, CREATOR));
        assertFalse(off.visible(CREATOR).contains(JoinMeSubCommands.PREVIEW));
        assertEquals(List.of(), off.suggestions(CREATOR, "p"));
        assertTrue(off.allowed(JoinMeSubCommands.CREATE, CREATOR));
        assertTrue(JoinMeSubCommands.SWITCHES.contains(JoinMeSubCommands.PREVIEW));
    }
}
