package net.prismarin.lobby.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SocialEntriesSettingsTest {

    @TempDir
    Path directory;

    @Test
    void freshFileOffersTheNickToolAndTheSilentLobby() {
        LobbySettings settings = LobbySettings.load(directory.resolve("config.yml"), "world");
        HotbarItem nick = settings.item(LobbySettings.NICK_TOOL_ITEM);
        assertEquals(2, nick.slot());
        assertEquals("NAME_TAG", nick.material());
        assertEquals("", nick.permission());
        HotbarItem silent = settings.item(LobbySettings.SILENT_ITEM);
        assertEquals(6, silent.slot());
        assertEquals("ECHO_SHARD", silent.hiddenMaterial());
        assertEquals(LobbySettings.SILENT_PERMISSION, silent.permission());
        assertTrue(settings.enabled(LobbySettings.NICK_TOOL));
        assertTrue(settings.enabled(LobbySettings.SILENT_LOBBY));
        assertEquals(List.of(new ProfileLayout.Entry(ProfileLayout.Entry.Kind.FRIENDS, -1),
                new ProfileLayout.Entry(ProfileLayout.Entry.Kind.PARTY, -1),
                new ProfileLayout.Entry(ProfileLayout.Entry.Kind.CLAN, -1)), settings.profile().social());
        assertEquals(List.of(), settings.warnings());
    }

    @Test
    void existingFilesGetTheSocialEntriesAddedOnce() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, """
                profile:
                  menu:
                    entries:
                      currencies: auto
                      language: 16
                """);
        LobbySettings settings = LobbySettings.load(file);
        assertEquals(List.of(ProfileLayout.Entry.Kind.FRIENDS, ProfileLayout.Entry.Kind.PARTY,
                        ProfileLayout.Entry.Kind.CLAN),
                settings.profile().social().stream().map(ProfileLayout.Entry::kind).toList());
        String text = Files.readString(file);
        assertTrue(text.contains("friends: auto"));
        assertTrue(text.contains("party: auto"));
        assertTrue(text.contains("clan: auto"));
        LobbySettings.load(file);
        assertEquals(text, Files.readString(file));
    }

    @Test
    void socialEntriesCanBePinnedOrHidden() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, """
                items:
                  silent-lobby:
                    active-material: ENDER_EYE
                    permission: rank.vip
                  nick-tool:
                    slot: 3
                    permission: nick.tool
                profile:
                  menu:
                    entries:
                      friends: 12
                      party: off
                """);
        LobbySettings settings = LobbySettings.load(file);
        assertEquals(List.of(new ProfileLayout.Entry(ProfileLayout.Entry.Kind.FRIENDS, 12),
                new ProfileLayout.Entry(ProfileLayout.Entry.Kind.CLAN, -1)), settings.profile().social());
        assertEquals(List.of(new ProfileLayout.Entry(ProfileLayout.Entry.Kind.FRIENDS, 12),
                        new ProfileLayout.Entry(ProfileLayout.Entry.Kind.CLAN, -1)),
                settings.profile().allEntries().stream()
                        .filter(entry -> entry.kind().social()).toList());
        assertEquals("ENDER_EYE", settings.item(LobbySettings.SILENT_ITEM).hiddenMaterial());
        assertEquals("rank.vip", settings.item(LobbySettings.SILENT_ITEM).permission());
        assertEquals(3, settings.item(LobbySettings.NICK_TOOL_ITEM).slot());
        assertEquals("nick.tool", settings.item(LobbySettings.NICK_TOOL_ITEM).permission());
    }
}
