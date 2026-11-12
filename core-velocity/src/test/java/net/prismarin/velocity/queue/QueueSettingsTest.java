package net.prismarin.velocity.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.bossbar.BossBar;
import net.prismarin.api.queue.QueueTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QueueSettingsTest {

    @TempDir
    Path directory;

    private QueueSettings load(String yaml) throws IOException {
        Path file = directory.resolve(QueueSettings.FILE_NAME);
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        return QueueSettings.load(file);
    }

    @Test
    void defaultsAreWrittenOnceWithComments() throws IOException {
        Path file = directory.resolve(QueueSettings.FILE_NAME);
        QueueSettings settings = QueueSettings.load(file);
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.startsWith("# PRISMARIN server queue on the proxy."));
        assertTrue(written.contains("# Priority levels: name: weight."));
        assertTrue(written.contains("hold-for-parties: true"));
        assertFalse(written.contains("—"));
        assertTrue(settings.warnings().isEmpty(), settings.warnings().toString());
        assertEquals(QueueRules.DEFAULT, settings.defaults());
        assertTrue(settings.onePerPlayer());
        assertTrue(settings.leaveOnServerSwitch());
        assertEquals(60, settings.graceSeconds());
        assertEquals(1_000, settings.tickMillis());
        assertEquals(List.of("queue"), settings.commands());
        assertEquals(List.of("leavequeue"), settings.leaveCommands());
        assertTrue(settings.display().actionBar());
        assertFalse(settings.display().bossBar());
        assertTrue(settings.party().together());
        assertTrue(settings.party().membersAlone());
        assertFalse(settings.rules(QueueTarget.group("lobby"), "lobby").enabled());
        assertFalse(settings.serverRules("lobby-1", "lobby").enabled());
        QueueSettings.load(file);
        assertEquals(written, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void groupAndServerOverridesStackOnTheDefaults() throws IOException {
        QueueSettings settings = load("""
                defaults:
                  max-players: 0
                  respect-game-state: true
                groups:
                  bedwars:
                    max-players: 12
                    hold-for-parties: false
                servers:
                  bw-1:
                    enabled: false
                  bw-2:
                    respect-game-state: false
                """);
        QueueRules group = settings.rules(QueueTarget.group("bedwars"), "bedwars");
        assertEquals(12, group.maxPlayers());
        assertFalse(group.holdForParties());
        assertTrue(group.enabled());
        assertFalse(settings.rules(QueueTarget.server("bw-1"), "bedwars").enabled());
        assertEquals(12, settings.serverRules("BW-1", "bedwars").maxPlayers());
        QueueRules second = settings.serverRules("bw-2", "bedwars");
        assertFalse(second.respectGameState());
        assertTrue(second.enabled());
        assertFalse(settings.serverRules("bw-2", null).respectGameState());
        assertTrue(settings.serverRules("bw-3", null).respectGameState());
        assertEquals(0, settings.rules(QueueTarget.group("skywars"), "skywars").maxPlayers());
    }

    @Test
    void highestMatchingPriorityWins() throws IOException {
        QueueSettings settings = load("""
                priorities:
                  vip: 10
                  mvp: 20
                  staff: 100
                """);
        Set<String> vip = Set.of("prismarin.queue.priority.vip");
        Set<String> both = Set.of("prismarin.queue.priority.vip", "prismarin.queue.priority.mvp");
        assertEquals(10, settings.priority(vip::contains));
        assertEquals(20, settings.priority(both::contains));
        assertEquals(0, settings.priority(permission -> false));
        assertEquals(100, settings.priority(permission -> true));
    }

    @Test
    void bypassCoversEveryQueueOrOneTargetOrItsGroup() {
        assertTrue(QueueSettings.bypasses(Set.of("prismarin.queue.bypass")::contains, QueueTarget.server("bw-1"),
                "bedwars"));
        assertTrue(QueueSettings.bypasses(Set.of("prismarin.queue.bypass.bw-1")::contains,
                QueueTarget.server("bw-1"), "bedwars"));
        assertTrue(QueueSettings.bypasses(Set.of("prismarin.queue.bypass.bedwars")::contains,
                QueueTarget.server("bw-1"), "bedwars"));
        assertTrue(QueueSettings.bypasses(Set.of("prismarin.queue.bypass.bedwars")::contains,
                QueueTarget.group("bedwars"), "bedwars"));
        assertFalse(QueueSettings.bypasses(Set.of("prismarin.queue.bypass.skywars")::contains,
                QueueTarget.server("bw-1"), "bedwars"));
    }

    @Test
    void switchedOffSubCommandsDisappear() throws IOException {
        QueueSettings settings = load("""
                sub-commands:
                  pause: false
                  leave: false
                  dance: false
                """);
        assertFalse(settings.subCommands().enabled("pause"));
        assertFalse(settings.subCommands().enabled("resume"));
        assertFalse(settings.subCommands().on("leave"));
        assertEquals(List.of("join", "list", "clear", "send", "help"), settings.subCommands().visible());
        assertTrue(settings.warnings().stream().anyMatch(warning -> warning.startsWith("sub-commands.dance")));
    }

    @Test
    void invalidValuesFallBackWithWarnings() throws IOException {
        QueueSettings settings = load("""
                tick-millis: 10
                reconnect-grace-seconds: -5
                display:
                  boss-bar-color: rainbow
                  boss-bar-style: wavy
                priorities:
                  vip: 0
                  "bad name": 5
                commands:
                  - "/Queue"
                  - "no spaces allowed"
                groups:
                  bedwars: 12
                  skywars:
                    speed: fast
                """);
        assertEquals(250, settings.tickMillis());
        assertEquals(0, settings.graceSeconds());
        assertEquals(BossBar.Color.BLUE, settings.display().color());
        assertEquals(BossBar.Overlay.PROGRESS, settings.display().overlay());
        assertTrue(settings.priorities().isEmpty());
        assertEquals(List.of("queue"), settings.commands());
        assertTrue(settings.groups().containsKey("skywars"));
        assertFalse(settings.groups().containsKey("bedwars"));
        List<String> warnings = settings.warnings();
        for (String expected : List.of("tick-millis", "reconnect-grace-seconds", "display.boss-bar-color",
                "display.boss-bar-style", "priorities.vip", "priorities.bad name", "commands", "groups.bedwars",
                "groups.skywars.speed")) {
            assertTrue(warnings.stream().anyMatch(warning -> warning.startsWith(expected)), expected + " " + warnings);
        }
    }

    @Test
    void bossBarAndDisplayOptionsAreRead() throws IOException {
        QueueSettings settings = load("""
                display:
                  action-bar: false
                  boss-bar: true
                  boss-bar-color: purple
                  boss-bar-style: notched_10
                  interval-seconds: 5
                party:
                  together: false
                  members-queue-alone: false
                one-queue-per-player: false
                """);
        assertFalse(settings.display().actionBar());
        assertTrue(settings.display().bossBar());
        assertEquals(BossBar.Color.PURPLE, settings.display().color());
        assertEquals(BossBar.Overlay.NOTCHED_10, settings.display().overlay());
        assertEquals(5, settings.display().intervalSeconds());
        assertFalse(settings.party().together());
        assertFalse(settings.party().membersAlone());
        assertFalse(settings.onePerPlayer());
    }

    @Test
    void pauseAndResumeShareOneStaffPermission() {
        assertEquals("prismarin.staff.queue.pause", QueueSettings.staffPermission("resume"));
        assertEquals("prismarin.staff.queue.pause", QueueSettings.staffPermission("pause"));
        assertEquals("prismarin.staff.queue.send", QueueSettings.staffPermission("send"));
        assertEquals(5_000L, QueueSettings.standard().retryMillis());
        assertEquals(60_000L, QueueSettings.standard().graceMillis());
    }
}
