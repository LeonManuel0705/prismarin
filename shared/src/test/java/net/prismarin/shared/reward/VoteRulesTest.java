package net.prismarin.shared.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.prismarin.api.reward.Reward;
import org.junit.jupiter.api.Test;

class VoteRulesTest {

    private final VoteRules rules = new VoteRules(Reward.currency("coins", 100L),
            Map.of("Minecraft-Server.EU", Reward.currency("coins", 150L)), Map.of(7, Reward.currency("gems", 5L)));

    @Test
    void aSiteRewardReplacesTheDefault() {
        assertEquals(Map.of("coins", 150L), rules.reward("minecraft-server.eu", null).currencies());
        assertEquals(Map.of("coins", 100L), rules.reward("other", null).currencies());
        assertEquals(Map.of("coins", 100L), rules.reward(null, null).currencies());
    }

    @Test
    void milestonesOnlyComeWithTheFirstVoteOfTheDay() {
        VoteLedger.Counted firstToday = new VoteLedger.Counted(20L, 7, 7, true, 0);
        VoteLedger.Counted secondToday = new VoteLedger.Counted(21L, 7, 7, false, 0);

        assertTrue(rules.milestone(firstToday));
        assertFalse(rules.milestone(secondToday));
        assertEquals(Map.of("coins", 100L, "gems", 5L), rules.reward("other", firstToday).currencies());
        assertEquals(Map.of("coins", 100L), rules.reward("other", secondToday).currencies());
    }
}
