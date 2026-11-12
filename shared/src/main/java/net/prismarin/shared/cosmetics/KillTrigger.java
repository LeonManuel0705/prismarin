package net.prismarin.shared.cosmetics;

public final class KillTrigger {

    public enum Verdict {
        PLAY,
        AUTO_OFF,
        NO_KILLER,
        SELF,
        EFFECT_ENTITY,
        MOB_KILL,
        CATEGORY_OFF,
        HIDDEN_KILLER
    }

    public record Kill(boolean killer, boolean self, boolean victimPlayer, boolean victimEffect) {
    }

    private KillTrigger() {
        throw new AssertionError();
    }

    public static Verdict judge(boolean auto, boolean mobKills, Kill kill, boolean categoryActive,
                                boolean killerShows) {
        if (!auto) {
            return Verdict.AUTO_OFF;
        }
        if (!kill.killer()) {
            return Verdict.NO_KILLER;
        }
        if (kill.self()) {
            return Verdict.SELF;
        }
        if (kill.victimEffect()) {
            return Verdict.EFFECT_ENTITY;
        }
        if (!kill.victimPlayer() && !mobKills) {
            return Verdict.MOB_KILL;
        }
        if (!categoryActive) {
            return Verdict.CATEGORY_OFF;
        }
        if (!killerShows) {
            return Verdict.HIDDEN_KILLER;
        }
        return Verdict.PLAY;
    }
}
