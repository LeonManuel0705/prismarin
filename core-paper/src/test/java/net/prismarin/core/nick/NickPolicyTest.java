package net.prismarin.core.nick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NickPolicyTest {

    private static NickPolicy policy(NickPolicy.Where where, NickPolicy.When when, boolean autoNick) {
        return new NickPolicy(where, when, autoNick, Set.of("Lobby"), Set.of("game", "bedwars"));
    }

    @Test
    void everywhereAllowsLobbies() {
        NickPolicy policy = policy(NickPolicy.Where.EVERYWHERE, NickPolicy.When.ALWAYS, true);
        assertTrue(policy.serverAllows("lobby", false));
        assertTrue(policy.serverAllows("build", false));
    }

    @Test
    void nonLobbyExcludesLobbyGroupsCaseInsensitive() {
        NickPolicy policy = policy(NickPolicy.Where.NON_LOBBY, NickPolicy.When.ALWAYS, true);
        assertFalse(policy.serverAllows("LOBBY", false));
        assertTrue(policy.serverAllows("build", false));
        assertTrue(policy.serverAllows("default", false));
        assertTrue(policy.serverAllows(null, false));
    }

    @Test
    void roundServersNeedARoundGroupOrAReportedGameState() {
        NickPolicy policy = policy(NickPolicy.Where.ROUND_SERVERS, NickPolicy.When.ALWAYS, true);
        assertTrue(policy.serverAllows("BedWars", false));
        assertFalse(policy.serverAllows("build", false));
        assertTrue(policy.serverAllows("build", true));
        assertFalse(policy.serverAllows("lobby", true));
    }

    @Test
    void alwaysShowsAnExistingNickOnJoin() {
        NickPolicy policy = policy(NickPolicy.Where.NON_LOBBY, NickPolicy.When.ALWAYS, false);
        assertEquals(NickPolicy.Action.NICK, policy.decide(NickPolicy.Moment.JOIN, true, false, true, false));
        assertEquals(NickPolicy.Action.REAL, policy.decide(NickPolicy.Moment.JOIN, false, false, true, false));
    }

    @Test
    void autoNickOnJoinNeedsServerSwitchAndPlayerSetting() {
        NickPolicy policy = policy(NickPolicy.Where.NON_LOBBY, NickPolicy.When.ALWAYS, true);
        assertEquals(NickPolicy.Action.GENERATE, policy.decide(NickPolicy.Moment.JOIN, true, false, false, true));
        assertEquals(NickPolicy.Action.REAL, policy.decide(NickPolicy.Moment.JOIN, true, false, false, false));
        NickPolicy manualOnly = policy(NickPolicy.Where.NON_LOBBY, NickPolicy.When.ALWAYS, false);
        assertEquals(NickPolicy.Action.REAL, manualOnly.decide(NickPolicy.Moment.JOIN, true, false, false, true));
    }

    @Test
    void autoNickIsNeverCreatedByAPlainCheck() {
        NickPolicy policy = policy(NickPolicy.Where.EVERYWHERE, NickPolicy.When.ALWAYS, true);
        assertEquals(NickPolicy.Action.REAL, policy.decide(NickPolicy.Moment.CHECK, true, true, false, true));
    }

    @Test
    void inRoundHidesTheNickOutsideOfRounds() {
        NickPolicy policy = policy(NickPolicy.Where.EVERYWHERE, NickPolicy.When.IN_ROUND, true);
        assertEquals(NickPolicy.Action.REAL, policy.decide(NickPolicy.Moment.JOIN, true, false, true, true));
        assertEquals(NickPolicy.Action.REAL, policy.decide(NickPolicy.Moment.CHECK, true, false, true, true));
        assertEquals(NickPolicy.Action.NICK, policy.decide(NickPolicy.Moment.CHECK, true, true, true, false));
    }

    @Test
    void autoNickOnlyInRoundsStartsWithTheRound() {
        NickPolicy policy = policy(NickPolicy.Where.NON_LOBBY, NickPolicy.When.IN_ROUND, true);
        assertEquals(NickPolicy.Action.REAL, policy.decide(NickPolicy.Moment.JOIN, true, false, false, true));
        assertEquals(NickPolicy.Action.GENERATE,
                policy.decide(NickPolicy.Moment.ROUND_START, true, true, false, true));
        assertEquals(NickPolicy.Action.GENERATE, policy.decide(NickPolicy.Moment.JOIN, true, true, false, true));
        assertEquals(NickPolicy.Action.REAL,
                policy.decide(NickPolicy.Moment.ROUND_START, false, true, false, true));
    }

    @Test
    void keysParseWithUnderscoresAndCase() {
        assertEquals(Optional.of(NickPolicy.Where.NON_LOBBY), NickPolicy.Where.parse(" Non_Lobby "));
        assertEquals(Optional.of(NickPolicy.Where.ROUND_SERVERS), NickPolicy.Where.parse("round-servers"));
        assertEquals(Optional.of(NickPolicy.When.IN_ROUND), NickPolicy.When.parse("IN_ROUND"));
        assertEquals(Optional.empty(), NickPolicy.When.parse("sometimes"));
        assertEquals(Optional.empty(), NickPolicy.Where.parse(null));
    }
}
