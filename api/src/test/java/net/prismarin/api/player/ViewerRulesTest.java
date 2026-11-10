package net.prismarin.api.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

class ViewerRulesTest {

    private static final RankDisplay FAKE = new RankDisplay("Player", Component.text("Player"), Component.empty(),
            NamedTextColor.GRAY, null, 0);
    private static final RankDisplay ADMIN = new RankDisplay("Admin", Component.text("Admin"), Component.empty(),
            NamedTextColor.RED, null, 100);

    private final UUID leon = UUID.randomUUID();
    private final UUID tim = UUID.randomUUID();
    private final UUID ghost = UUID.randomUUID();
    private final UUID viewer = UUID.randomUUID();

    private final NetworkPlayer nicked = player(leon, "Leon", "SilentFox", true, false);
    private final NetworkPlayer plain = player(tim, "Tim", "", true, false);
    private final NetworkPlayer vanished = player(ghost, "Ghost", "", true, true);

    @Test
    void realNamesAreRevealedToThemselvesStaffAndTheConsole() {
        assertTrue(ViewerRules.revealsRealName(leon, leon, false));
        assertTrue(ViewerRules.revealsRealName(viewer, leon, true));
        assertTrue(ViewerRules.revealsRealName(null, leon, false));
        assertFalse(ViewerRules.revealsRealName(viewer, leon, false));
    }

    @Test
    void displayNameAndRankFollowTheRevealRule() {
        assertEquals("SilentFox", ViewerRules.displayName(nicked, false));
        assertEquals("Leon", ViewerRules.displayName(nicked, true));
        assertEquals("Tim", ViewerRules.displayName(plain, false));
        assertEquals(FAKE, ViewerRules.rank(nicked, false));
        assertEquals(ADMIN, ViewerRules.rank(nicked, true));
    }

    @Test
    void vanishedPlayersAreOnlySeenByThemselvesStaffAndTheConsole() {
        assertFalse(ViewerRules.canSee(viewer, vanished, false));
        assertTrue(ViewerRules.canSee(viewer, vanished, true));
        assertTrue(ViewerRules.canSee(ghost, vanished, false));
        assertTrue(ViewerRules.canSee(null, vanished, false));
        assertTrue(ViewerRules.canSee(viewer, plain, false));
    }

    @Test
    void typingTheNickFindsTheNickedPlayer() {
        Optional<NetworkPlayer> found = resolve("silentfox", false);
        assertEquals(Optional.of(nicked), found);
    }

    @Test
    void typingTheRealNameOfANickedPlayerShowsHimOffline() {
        NetworkPlayer found = resolve("Leon", false).orElseThrow();
        assertEquals(leon, found.uuid());
        assertFalse(found.online());
        assertFalse(found.nicked());
        assertEquals("", found.server());
        assertEquals(ADMIN, found.rank());
    }

    @Test
    void staffFindsNickedPlayersByTheirRealName() {
        assertEquals(Optional.of(nicked), resolve("leon", true));
        assertEquals(Optional.of(nicked), resolve("SilentFox", true));
    }

    @Test
    void thePlayerFindsHimselfByHisRealName() {
        Optional<NetworkPlayer> found = ViewerRules.resolve(List.of(nicked, plain), "Leon",
                target -> ViewerRules.revealsRealName(leon, target.uuid(), false), target -> true);
        assertEquals(Optional.of(nicked), found);
    }

    @Test
    void vanishedPlayersLookOfflineAndNicksOfHiddenPlayersFindNobody() {
        NetworkPlayer hiddenNick = player(ghost, "Ghost", "Shadow", true, true);
        Predicate<NetworkPlayer> visible = target -> ViewerRules.canSee(viewer, target, false);
        Predicate<NetworkPlayer> revealed = target -> false;
        NetworkPlayer found = ViewerRules.resolve(List.of(vanished), "Ghost", revealed, visible).orElseThrow();
        assertFalse(found.online());
        assertEquals(Optional.empty(), ViewerRules.resolve(List.of(hiddenNick), "Shadow", revealed, visible));
    }

    @Test
    void offlinePlayersResolveByRealNameAndUnknownNamesFindNobody() {
        NetworkPlayer offline = player(UUID.randomUUID(), "Anna", "", false, false);
        assertEquals(Optional.of(offline), ViewerRules.resolve(List.of(offline), "anna", target -> false,
                target -> true));
        assertEquals(Optional.empty(), resolve("Nobody", false));
        assertEquals(Optional.empty(), resolve("two words", false));
        assertEquals(Optional.empty(), resolve("", false));
        assertEquals(Optional.empty(), resolve(null, false));
    }

    @Test
    void suggestionsNeverLeakRealNamesOrHiddenPlayers() {
        Predicate<NetworkPlayer> visible = target -> ViewerRules.canSee(viewer, target, false);
        assertEquals(List.of("SilentFox", "Tim"), ViewerRules.suggestions(List.of(nicked, plain, vanished), "",
                target -> false, visible));
        assertEquals(List.of("Leon"), ViewerRules.suggestions(List.of(nicked, plain, vanished), "le",
                target -> true, visible));
        assertEquals(List.of("Ghost", "Leon", "SilentFox", "Tim"),
                ViewerRules.suggestions(List.of(nicked, plain, vanished), null, target -> true, target -> true));
    }

    private Optional<NetworkPlayer> resolve(String typed, boolean privileged) {
        return ViewerRules.resolve(List.of(plain, nicked, vanished), typed,
                target -> ViewerRules.revealsRealName(viewer, target.uuid(), privileged),
                target -> ViewerRules.canSee(viewer, target, false));
    }

    private static NetworkPlayer player(UUID uuid, String name, String nick, boolean online, boolean vanished) {
        RankDisplay shown = nick.isEmpty() ? ADMIN : FAKE;
        return new NetworkPlayer(uuid, name, nick, Locale.GERMAN, "bw-1", "world", online, vanished, shown, ADMIN,
                3, 0L, NetworkPlayer.Source.LOCAL);
    }
}
