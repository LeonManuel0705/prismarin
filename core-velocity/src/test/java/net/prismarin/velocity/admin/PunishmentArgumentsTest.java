package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Set;
import java.util.function.Predicate;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.velocity.admin.PunishmentArguments.Command;
import org.junit.jupiter.api.Test;

class PunishmentArgumentsTest {

    private static final Predicate<String> TEMPLATES = id -> Set.of("hacking", "chat").contains(id.toLowerCase());

    private static PunishmentArguments.Parsed parse(Command command, String line) {
        return PunishmentArguments.parse(command, line.isEmpty() ? new String[0] : line.split(" "), TEMPLATES);
    }

    @Test
    void banIsPermanentAndTakesTheWholeReason() {
        PunishmentArguments.Manual ban = assertInstanceOf(PunishmentArguments.Manual.class,
                parse(Command.BAN, "Steve flying around spawn"));
        assertEquals("Steve", ban.target());
        assertEquals(PunishmentType.BAN, ban.type());
        assertNull(ban.duration());
        assertEquals("flying around spawn", ban.reason());
        assertEquals(PunishmentKind.BAN, ban.kind());
    }

    @Test
    void banStillAcceptsTheOldDurationSyntax() {
        PunishmentArguments.Manual ban = assertInstanceOf(PunishmentArguments.Manual.class,
                parse(Command.BAN, "Steve 7d griefing"));
        assertEquals(Duration.ofDays(7), ban.duration());
        assertEquals(PunishmentKind.TEMPBAN, ban.kind());
        assertInstanceOf(PunishmentArguments.Usage.class, parse(Command.BAN, "Steve 7d"));
    }

    @Test
    void tempCommandsNeedADuration() {
        PunishmentArguments.Manual mute = assertInstanceOf(PunishmentArguments.Manual.class,
                parse(Command.TEMPMUTE, "Steve 1h30m caps"));
        assertEquals(Duration.ofMinutes(90), mute.duration());
        assertEquals(PunishmentKind.TEMPMUTE, mute.kind());
        assertEquals("soon", assertInstanceOf(PunishmentArguments.BadDuration.class,
                parse(Command.TEMPBAN, "Steve soon spam")).text());
        assertInstanceOf(PunishmentArguments.Usage.class, parse(Command.TEMPBAN, "Steve 1d"));
        assertInstanceOf(PunishmentArguments.Usage.class, parse(Command.TEMPBAN, "Steve"));
    }

    @Test
    void aTemplateIdAppliesTheTemplate() {
        PunishmentArguments.Template template = assertInstanceOf(PunishmentArguments.Template.class,
                parse(Command.BAN, "Steve Hacking killaura in bedwars"));
        assertEquals("hacking", template.id());
        assertEquals("killaura in bedwars", template.note());
        assertInstanceOf(PunishmentArguments.Template.class, parse(Command.TEMPMUTE, "Steve chat"));
        assertInstanceOf(PunishmentArguments.Template.class, parse(Command.WARN, "Steve chat"));
        assertInstanceOf(PunishmentArguments.Manual.class, parse(Command.IPBAN, "Steve hacking"));
    }

    @Test
    void punishOpensTheMenuWithoutATemplate() {
        assertEquals("Steve", assertInstanceOf(PunishmentArguments.Menu.class, parse(Command.PUNISH, "Steve"))
                .target());
        assertEquals("", assertInstanceOf(PunishmentArguments.Template.class, parse(Command.PUNISH, "Steve chat"))
                .note());
        assertEquals("flying", assertInstanceOf(PunishmentArguments.UnknownTemplate.class,
                parse(Command.PUNISH, "Steve flying")).id());
        assertInstanceOf(PunishmentArguments.Usage.class, parse(Command.PUNISH, ""));
    }

    @Test
    void kicksWorkWithoutAReasonButWarningsDoNot() {
        PunishmentArguments.Manual kick = assertInstanceOf(PunishmentArguments.Manual.class,
                parse(Command.KICK, "Steve"));
        assertEquals("", kick.reason());
        assertEquals(PunishmentKind.KICK, kick.kind());
        assertInstanceOf(PunishmentArguments.Usage.class, parse(Command.WARN, "Steve"));
        assertEquals("1d", assertInstanceOf(PunishmentArguments.Manual.class, parse(Command.WARN, "Steve 1d"))
                .reason());
    }

    @Test
    void ipBansAreMarked() {
        PunishmentArguments.Manual ban = assertInstanceOf(PunishmentArguments.Manual.class,
                parse(Command.IPBAN, "Steve 30d alts"));
        assertTrue(ban.address());
        assertEquals(PunishmentKind.IPBAN, ban.kind());
        assertEquals(Duration.ofDays(30), ban.duration());
    }

    @Test
    void emptyReasonsFallBackToTheUsage() {
        assertInstanceOf(PunishmentArguments.Usage.class, PunishmentArguments.parse(Command.MUTE,
                new String[]{"Steve", ""}, TEMPLATES));
        assertEquals("a b", PunishmentArguments.rest(new String[]{"x", "a", "b"}, 1));
        assertEquals("", PunishmentArguments.rest(new String[]{"x"}, 1));
    }
}
