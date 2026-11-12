package net.prismarin.velocity.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubCommandsTest {

    @TempDir
    Path directory;

    @Test
    void switchingPromoteOffHidesItsCounterpartToo() {
        SubCommands party = SubCommands.PARTY.withOff(Set.of("promote"));
        assertFalse(party.enabled("promote"));
        assertFalse(party.enabled("demote"));
        assertTrue(party.enabled("transfer"));
        assertEquals(List.of("invite", "accept", "deny", "join", "leave", "kick", "transfer", "disband", "list",
                "warp", "chat", "settings", "help"), party.visible());
    }

    @Test
    void clanSwitchesHideGroupsOfCommands() {
        SubCommands clans = SubCommands.CLANS.withOff(Set.of("invite", "rename", "color", "unknown"));
        assertEquals(Set.of("invite", "rename", "color"), clans.off());
        for (String hidden : List.of("invite", "accept", "deny", "rename", "tag", "color")) {
            assertFalse(clans.visible().contains(hidden), hidden);
        }
        assertTrue(clans.visible().containsAll(List.of("create", "info", "join", "leave", "kick", "list", "help")));
        assertTrue(clans.on("join"));
        assertFalse(clans.on("invite"));
        assertTrue(clans.enabled("anything-else"));
    }

    @Test
    void everythingIsOnByDefault() {
        for (SubCommands commands : List.of(SubCommands.FRIENDS, SubCommands.PARTY, SubCommands.CLANS,
                SubCommands.MESSAGES)) {
            assertEquals(commands.actions(), commands.visible());
            for (List<String> group : commands.switches().values()) {
                assertTrue(commands.actions().containsAll(group) || commands == SubCommands.MESSAGES, group.toString());
            }
        }
    }

    @Test
    void socialYmlSwitchesReachTheSettings() throws IOException {
        Path file = directory.resolve(SocialSettings.FILE_NAME);
        Files.writeString(file, """
                friends:
                  sub-commands:
                    jump: false
                    settings: false
                party:
                  sub-commands:
                    promote: false
                    join: false
                    chat: false
                    teleport: false
                messages:
                  staff-spy: true
                  sub-commands:
                    toggle: false
                clans:
                  sub-commands:
                    disband: false
                """);
        SocialSettings settings = SocialSettings.load(file);
        assertFalse(settings.friends().subCommands().visible().contains("jump"));
        assertFalse(settings.friends().subCommands().visible().contains("settings"));
        assertFalse(settings.party().subCommands().visible().contains("demote"));
        assertEquals(List.of(PartyOption.INVITE, PartyOption.FOLLOW), settings.party().options());
        assertFalse(settings.messages().subCommands().on("toggle"));
        assertTrue(settings.messages().subCommands().on("reply"));
        assertFalse(settings.clans().subCommands().enabled("disband"));
        assertEquals(1, settings.warnings().size());
        assertTrue(settings.warnings().getFirst().startsWith("party.sub-commands.teleport"));
        String text = Files.readString(file);
        assertTrue(text.contains("    warp: true"));
        assertTrue(text.contains("    rename: true"));
    }

    @Test
    void clanNamesAndTagsAreChecked() {
        ClanNaming naming = new ClanNaming(3, 8, 2, 4, Pattern.compile("[A-Za-z0-9_]+"), Set.of("Admin"));
        assertEquals(ClanNaming.Check.OK, naming.name("Owls_1"));
        assertEquals(ClanNaming.Check.TOO_SHORT, naming.name("Ow"));
        assertEquals(ClanNaming.Check.TOO_LONG, naming.name("Nightowlss"));
        assertEquals(ClanNaming.Check.CHARACTERS, naming.name("Owl Pack"));
        assertEquals(ClanNaming.Check.BLOCKED, naming.name("ADMIN"));
        assertEquals(ClanNaming.Check.OK, naming.tag("OWL"));
        assertEquals(ClanNaming.Check.TOO_LONG, naming.tag("OWLSS"));
        assertEquals(ClanNaming.Check.CHARACTERS, naming.tag("O&L"));
        assertEquals(ClanNaming.Check.TOO_SHORT, naming.tag(null));
    }
}
