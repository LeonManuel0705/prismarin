package net.prismarin.shared.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.prismarin.shared.cosmetics.KillTrigger.Kill;
import net.prismarin.shared.cosmetics.KillTrigger.Verdict;
import org.junit.jupiter.api.Test;

class KillTriggerTest {

    private static final Kill PLAYER_KILL = new Kill(true, false, true, false);
    private static final Kill MOB_KILL = new Kill(true, false, false, false);

    @Test
    void aPlayerKillPlaysTheEffect() {
        assertEquals(Verdict.PLAY, KillTrigger.judge(true, false, PLAYER_KILL, true, true));
    }

    @Test
    void mobKillsOnlyCountWhenSwitchedOn() {
        assertEquals(Verdict.MOB_KILL, KillTrigger.judge(true, false, MOB_KILL, true, true));
        assertEquals(Verdict.PLAY, KillTrigger.judge(true, true, MOB_KILL, true, true));
    }

    @Test
    void aGamePluginCanSwitchTheAutomaticTriggerOff() {
        assertEquals(Verdict.AUTO_OFF, KillTrigger.judge(false, true, PLAYER_KILL, true, true));
    }

    @Test
    void anInactiveCategoryOrAHiddenKillerPlaysNothing() {
        assertEquals(Verdict.CATEGORY_OFF, KillTrigger.judge(true, false, PLAYER_KILL, false, true));
        assertEquals(Verdict.HIDDEN_KILLER, KillTrigger.judge(true, false, PLAYER_KILL, true, false));
    }

    @Test
    void deathsWithoutAKillerSelfKillsAndEffectEntitiesAreIgnored() {
        assertEquals(Verdict.NO_KILLER, KillTrigger.judge(true, true, new Kill(false, false, true, false), true,
                true));
        assertEquals(Verdict.SELF, KillTrigger.judge(true, true, new Kill(true, true, true, false), true, true));
        assertEquals(Verdict.EFFECT_ENTITY, KillTrigger.judge(true, true, new Kill(true, false, false, true), true,
                true));
    }
}
