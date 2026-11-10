package net.prismarin.velocity.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.prismarin.api.social.PartyAccess;
import net.prismarin.api.social.PartyRole;
import net.prismarin.api.social.PartySettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SocialSettingsTest {

    @TempDir
    Path directory;

    @Test
    void freshFileGetsDefaultsAndComments() throws IOException {
        Path file = directory.resolve(SocialSettings.FILE_NAME);
        SocialSettings settings = SocialSettings.load(file);
        assertEquals(100, settings.friends().maxFriends());
        assertEquals(Map.of("prismarin.friends.limit.vip", 200), settings.friends().limitPermissions());
        assertEquals(72L * 3_600_000L, settings.friends().requestExpiryMillis());
        assertEquals(List.of("friend", "friends", "f"), settings.friends().commands());
        assertEquals(8, settings.party().maxSize());
        assertEquals(60_000L, settings.party().inviteExpiryMillis());
        assertTrue(settings.party().follow());
        assertFalse(settings.party().followIntoLobbies());
        assertEquals(List.of("party", "p"), settings.party().commands());
        assertEquals(List.of("msg", "tell", "w", "whisper", "m"), settings.messages().commands());
        assertFalse(settings.messages().staffSpy());
        assertEquals(SocialSetting.EVERYONE, settings.defaultValue(SocialSetting.REQUESTS));
        assertEquals(SocialSetting.ON, settings.defaultValue(SocialSetting.NOTIFICATIONS));
        assertEquals(List.of(), settings.warnings());
        String text = Files.readString(file);
        assertTrue(text.startsWith("# PRISMARIN friends, parties and private messages on the proxy."));
        assertTrue(text.contains("# Parties live in the memory of this proxy and end with a restart."));
        assertEquals(text, reload(file));
    }

    @Test
    void readsOwnValuesAndBooleanLikeDefaults() throws IOException {
        Path file = directory.resolve(SocialSettings.FILE_NAME);
        Files.writeString(file, """
                friends:
                  max-friends: 250
                  limit-permissions:
                    rank.mvp: 500
                  commands: [Freunde, /fr]
                party:
                  max-size: 12
                  follow:
                    into-lobbies: true
                    skip-groups: [Build, CREATIVE]
                messages:
                  staff-spy: true
                defaults:
                  notifications: off
                  jump: false
                  messages: friends
                  party-invites: nobody
                """);
        SocialSettings settings = SocialSettings.load(file);
        assertEquals(250, settings.friends().maxFriends());
        assertEquals(Map.of("rank.mvp", 500), settings.friends().limitPermissions());
        assertEquals(List.of("freunde", "fr"), settings.friends().commands());
        assertEquals(12, settings.party().maxSize());
        assertTrue(settings.party().followIntoLobbies());
        assertEquals(Set.of("build", "creative"), settings.party().followSkipGroups());
        assertTrue(settings.messages().staffSpy());
        assertEquals(SocialSetting.OFF, settings.defaultValue(SocialSetting.NOTIFICATIONS));
        assertEquals(SocialSetting.OFF, settings.defaultValue(SocialSetting.JUMP));
        assertEquals(SocialSetting.FRIENDS, settings.defaultValue(SocialSetting.MESSAGES));
        assertEquals(SocialSetting.NOBODY, settings.defaultValue(SocialSetting.PARTY_INVITES));
        assertEquals(List.of(), settings.warnings());
    }

    @Test
    void clampsAndWarnsAboutBrokenValues() throws IOException {
        Path file = directory.resolve(SocialSettings.FILE_NAME);
        Files.writeString(file, """
                friends:
                  max-friends: 0
                  limit-permissions:
                    rank.zero: 0
                  commands: ["bad name!"]
                party:
                  max-size: 1000
                defaults:
                  requests: sometimes
                """);
        SocialSettings settings = SocialSettings.load(file);
        assertEquals(1, settings.friends().maxFriends());
        assertEquals(Map.of(), settings.friends().limitPermissions());
        assertEquals(List.of("friend", "friends", "f"), settings.friends().commands());
        assertEquals(100, settings.party().maxSize());
        assertEquals(SocialSetting.EVERYONE, settings.defaultValue(SocialSetting.REQUESTS));
        assertEquals(6, settings.warnings().size());
    }

    @Test
    void freshFilesGetPartyDefaults() throws IOException {
        Path file = directory.resolve(SocialSettings.FILE_NAME);
        SocialSettings settings = SocialSettings.load(file);
        assertEquals(PartySettings.DEFAULT, settings.party().defaults());
        assertEquals(List.of(PartyOption.INVITE, PartyOption.ACCESS, PartyOption.FOLLOW, PartyOption.CHAT),
                settings.party().options());
        String text = Files.readString(file);
        assertTrue(text.contains("    invite: moderators"));
        assertTrue(text.contains("    access: invite-only"));
        assertTrue(text.contains("# Who may invite: leader, moderators or members."));
        assertFalse(text.contains("members-can-invite"));
    }

    @Test
    void oldFilesKeepWorkingAndMoveTheInviteSwitch() throws IOException {
        Path file = directory.resolve(SocialSettings.FILE_NAME);
        Files.writeString(file, """
                party:
                  members-can-invite: true
                  follow:
                    enabled: false
                """);
        SocialSettings settings = SocialSettings.load(file);
        assertEquals(PartyRole.MEMBER, settings.party().defaults().invite());
        assertFalse(settings.party().follow());
        assertFalse(settings.party().options().contains(PartyOption.FOLLOW));
        assertEquals(List.of(), settings.warnings());
        String text = Files.readString(file);
        assertTrue(text.contains("members-can-invite: true"));
        assertTrue(text.contains("    invite: members"));
        assertTrue(text.contains("# Replaced by party.defaults.invite."));
        assertEquals(text, reload(file));
    }

    @Test
    void readsOwnPartyDefaultsAndWarnsAboutBrokenOnes() throws IOException {
        Path file = directory.resolve(SocialSettings.FILE_NAME);
        Files.writeString(file, """
                party:
                  defaults:
                    invite: leader
                    access: Public
                    follow: off
                    chat: sometimes
                """);
        SocialSettings settings = SocialSettings.load(file);
        assertEquals(new PartySettings(PartyRole.LEADER, PartyAccess.OPEN, false, PartyRole.MEMBER),
                settings.party().defaults());
        assertEquals(1, settings.warnings().size());
        assertTrue(settings.warnings().getFirst().startsWith("party.defaults.chat"));
    }

    @Test
    void brokenFileFallsBackToDefaults() throws IOException {
        Path file = directory.resolve(SocialSettings.FILE_NAME);
        Files.writeString(file, "friends: [unclosed");
        SocialSettings settings = SocialSettings.load(file);
        assertEquals(100, settings.friends().maxFriends());
        assertEquals(1, settings.warnings().size());
        assertEquals(SocialSettings.standard().party(), settings.party());
    }

    private static String reload(Path file) throws IOException {
        SocialSettings.load(file);
        return Files.readString(file);
    }
}
