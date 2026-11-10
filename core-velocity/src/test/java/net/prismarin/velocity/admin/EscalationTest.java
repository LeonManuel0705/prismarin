package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.PunishmentType;
import org.junit.jupiter.api.Test;

class EscalationTest {

    private static final long DAY = Duration.ofDays(1).toMillis();
    private static final long NOW = 1_000L * DAY;
    private final UUID player = UUID.randomUUID();

    private final PunishmentTemplate hacking = new PunishmentTemplate("hacking", Map.of("en", "Hacking"), "", "",
            List.of(new PunishmentTemplate.Step(PunishmentType.BAN, Duration.ofDays(30)),
                    new PunishmentTemplate.Step(PunishmentType.BAN, Duration.ofDays(90)),
                    new PunishmentTemplate.Step(PunishmentType.BAN, null)), null);

    private Punishment entry(String template, long createdAt, boolean lifted, String replacedBy) {
        return new Punishment(UUID.randomUUID().toString(), player, "Steve", PunishmentType.BAN, "x", "Leon",
                createdAt, createdAt + DAY, false, lifted || !replacedBy.isEmpty() ? "Mod" : "",
                lifted || !replacedBy.isEmpty() ? createdAt + 1 : 0L, template, "", "", "", replacedBy);
    }

    @Test
    void aCleanRecordStartsAtTheFirstStep() {
        Escalation.Outcome outcome = Escalation.next(hacking, List.of(), NOW, false);
        assertEquals(0, outcome.previous());
        assertEquals(1, outcome.offence());
        assertEquals(1, outcome.level());
        assertEquals(3, outcome.levels());
        assertEquals(Duration.ofDays(30), outcome.step().duration());
    }

    @Test
    void onlyOffencesOfTheSameTemplateCount() {
        List<Punishment> history = new ArrayList<>(List.of(entry("hacking", NOW - DAY, false, ""),
                entry("chat", NOW - DAY, false, ""), entry("", NOW - DAY, false, "")));
        assertEquals(Duration.ofDays(90), Escalation.next(hacking, history, NOW, false).step().duration());
        history.add(entry("HACKING", NOW - 2 * DAY, false, "next"));
        Escalation.Outcome outcome = Escalation.next(hacking, history, NOW, false);
        assertEquals(2, outcome.previous());
        assertEquals(PunishmentKind.BAN, outcome.step().kind());
        history.add(entry("hacking", NOW - 3 * DAY, false, ""));
        assertEquals(2, Escalation.next(hacking, history, NOW, false).stepIndex());
    }

    @Test
    void liftedPunishmentsOnlyCountWhenConfigured() {
        List<Punishment> history = List.of(entry("hacking", NOW - DAY, true, ""));
        assertEquals(0, Escalation.previousOffences(hacking, history, NOW, false));
        assertEquals(1, Escalation.previousOffences(hacking, history, NOW, true));
    }

    @Test
    void oldOffencesAreForgotten() {
        PunishmentTemplate forgiving = new PunishmentTemplate("chat", Map.of(), "", "",
                List.of(new PunishmentTemplate.Step(PunishmentType.MUTE, Duration.ofHours(1))), Duration.ofDays(30));
        List<Punishment> history = List.of(entry("chat", NOW - 40 * DAY, false, ""),
                entry("chat", NOW - 10 * DAY, false, ""));
        assertEquals(1, Escalation.previousOffences(forgiving, history, NOW, false));
    }
}
