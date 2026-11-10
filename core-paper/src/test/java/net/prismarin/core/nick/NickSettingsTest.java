package net.prismarin.core.nick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.prismarin.api.server.GameState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NickSettingsTest {

    @TempDir
    Path directory;

    @Test
    void firstStartWritesDefaultsWithComments() throws IOException {
        Path file = directory.resolve("nick.yml");
        NickSettings settings = NickSettings.load(file);

        assertEquals(NickPolicy.Where.EVERYWHERE, settings.policy().where());
        assertEquals(NickPolicy.When.ALWAYS, settings.policy().when());
        assertTrue(settings.policy().autoNick());
        assertEquals(Set.of("lobby"), settings.policy().lobbyGroups());
        assertEquals(Set.of("game"), settings.policy().roundGroups());
        assertEquals(Set.of(GameState.RUNNING), settings.roundStates());
        assertTrue(settings.actionBar());
        assertTrue(settings.names().generator());
        assertEquals(List.of(), settings.names().list());
        assertEquals(30, settings.names().claimExpiryDays());
        assertEquals(NickSettings.SkinMode.POOL, settings.skins().mode());
        assertEquals(NickSettings.DEFAULT_SKIN_POOL, settings.skins().pool());
        assertEquals("default", settings.fakeRank().group());
        assertEquals(-1, settings.fakeRank().weight());
        assertTrue(settings.fakeRank().luckPermsDisguise());
        assertEquals(List.of(), settings.problems());

        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.startsWith("# Nick settings of this server."));
        assertTrue(written.contains("# Where nicks are shown"));
        assertTrue(written.contains("where: everywhere"));
        assertTrue(written.contains("  # LuckPerms group whose prefix"));
        assertTrue(written.contains("Placeholders are written as %name%"));
    }

    @Test
    void reloadingKeepsTheFileStable() throws IOException {
        Path file = directory.resolve("nick.yml");
        NickSettings.load(file);
        String first = Files.readString(file, StandardCharsets.UTF_8);
        NickSettings.load(file);
        assertEquals(first, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void customValuesAreRead() throws IOException {
        Path file = directory.resolve("nick.yml");
        Files.writeString(file, """
                where: round-servers
                when: in_round
                auto-nick: false
                lobby-groups: [Hub]
                round-groups: [bedwars, skywars]
                round-states: [starting, RUNNING]
                action-bar: false
                names:
                  generator: false
                  list: [Alpha, "not valid", Bravo]
                  blocked: [mod]
                  claim-expiry-days: 0
                skins:
                  mode: OWN
                  pool: []
                  refresh-hours: 5
                fake-rank:
                  group: vip
                  prefix: "&aVIP"
                  name-color: green
                  weight: 20
                  perks: [prismarin.chat.format]
                  luckperms-disguise: false
                  luckperms-priority: 50
                """, StandardCharsets.UTF_8);

        NickSettings settings = NickSettings.load(file);

        assertEquals(NickPolicy.Where.ROUND_SERVERS, settings.policy().where());
        assertEquals(NickPolicy.When.IN_ROUND, settings.policy().when());
        assertFalse(settings.policy().autoNick());
        assertEquals(Set.of("hub"), settings.policy().lobbyGroups());
        assertEquals(Set.of("bedwars", "skywars"), settings.policy().roundGroups());
        assertEquals(Set.of(GameState.STARTING, GameState.RUNNING), settings.roundStates());
        assertFalse(settings.actionBar());
        assertFalse(settings.names().generator());
        assertEquals(List.of("Alpha", "Bravo"), settings.names().list());
        assertEquals(List.of("mod"), settings.names().blocked());
        assertEquals(1, settings.names().claimExpiryDays());
        assertEquals(NickSettings.SkinMode.OWN, settings.skins().mode());
        assertEquals(List.of(), settings.skins().pool());
        assertEquals(5, settings.skins().refreshHours());
        assertEquals(new NickSettings.FakeRank("vip", "&aVIP", "green", 20, List.of("prismarin.chat.format"),
                false, 50), settings.fakeRank());
        assertEquals(1, settings.problems().size());
        assertTrue(settings.problems().getFirst().contains("not valid"));
    }

    @Test
    void invalidValuesFallBackAndAreReported() throws IOException {
        Path file = directory.resolve("nick.yml");
        Files.writeString(file, """
                where: lobby-only
                when: sometimes
                round-states: [running, playing]
                skins:
                  mode: random
                fake-rank:
                  name-color: rainbow
                """, StandardCharsets.UTF_8);

        NickSettings settings = NickSettings.load(file);

        assertEquals(NickPolicy.Where.EVERYWHERE, settings.policy().where());
        assertEquals(NickPolicy.When.ALWAYS, settings.policy().when());
        assertEquals(Set.of(GameState.RUNNING), settings.roundStates());
        assertEquals(NickSettings.SkinMode.POOL, settings.skins().mode());
        assertEquals(5, settings.problems().size());
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.contains("where: lobby-only"));
        assertTrue(written.contains("claim-expiry-days: 30"));
    }

    @Test
    void brokenYamlUsesDefaultsWithoutTouchingTheFile() throws IOException {
        Path file = directory.resolve("nick.yml");
        String broken = "where: [unclosed\n  when: : :\n";
        Files.writeString(file, broken, StandardCharsets.UTF_8);

        NickSettings settings = NickSettings.load(file);

        assertEquals(NickPolicy.Where.EVERYWHERE, settings.policy().where());
        assertEquals(1, settings.problems().size());
        assertEquals(broken, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void defaultsMatchAFreshFile() {
        NickSettings defaults = NickSettings.defaults();
        NickSettings loaded = NickSettings.load(directory.resolve("fresh.yml"));
        assertEquals(defaults, loaded);
    }
}
