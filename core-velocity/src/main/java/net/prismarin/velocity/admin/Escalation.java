package net.prismarin.velocity.admin;

import java.util.List;
import net.prismarin.api.moderation.Punishment;

public final class Escalation {

    public record Outcome(PunishmentTemplate template, int previous, int stepIndex, PunishmentTemplate.Step step) {

        public int offence() {
            return previous + 1;
        }

        public int level() {
            return stepIndex + 1;
        }

        public int levels() {
            return template.steps().size();
        }
    }

    private Escalation() {
        throw new AssertionError();
    }

    public static int previousOffences(PunishmentTemplate template, List<Punishment> history, long now,
                                       boolean countLifted) {
        long since = template.forgetAfter() == null ? Long.MIN_VALUE : now - template.forgetAfter().toMillis();
        return (int) history.stream()
                .filter(entry -> entry.template().equalsIgnoreCase(template.id()))
                .filter(entry -> countLifted || !entry.lifted())
                .filter(entry -> entry.createdAt() >= since)
                .count();
    }

    public static Outcome next(PunishmentTemplate template, List<Punishment> history, long now, boolean countLifted) {
        int previous = previousOffences(template, history, now, countLifted);
        return new Outcome(template, previous, template.stepIndex(previous), template.step(previous));
    }
}
