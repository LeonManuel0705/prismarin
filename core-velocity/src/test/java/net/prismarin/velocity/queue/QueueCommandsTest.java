package net.prismarin.velocity.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import java.util.List;
import java.util.Set;
import net.prismarin.velocity.social.SubCommands;
import org.junit.jupiter.api.Test;

class QueueCommandsTest {

    private static Player player(Set<String> permissions) {
        Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenAnswer(call -> permissions.contains(call.getArgument(0)));
        return player;
    }

    @Test
    void playersOnlySeeTheirOwnActions() {
        Player player = player(Set.of());
        assertEquals(List.of("join", "leave", "help"), QueueCommands.visible(QueueSettings.SUB_COMMANDS, player));
        assertFalse(QueueCommands.allowed(QueueSettings.SUB_COMMANDS, player, "clear"));
    }

    @Test
    void staffSeeWhatTheirPermissionsAllow() {
        Player staff = player(Set.of("prismarin.staff.queue.list", "prismarin.staff.queue.pause"));
        assertEquals(List.of("join", "leave", "list", "pause", "resume", "help"),
                QueueCommands.visible(QueueSettings.SUB_COMMANDS, staff));
    }

    @Test
    void switchedOffActionsVanishEvenForStaff() {
        Player staff = player(Set.of("prismarin.staff.queue.list", "prismarin.staff.queue.pause",
                "prismarin.staff.queue.clear", "prismarin.staff.queue.send"));
        SubCommands sub = QueueSettings.SUB_COMMANDS.withOff(Set.of("join", "pause", "send"));
        assertEquals(List.of("leave", "list", "clear", "help"), QueueCommands.visible(sub, staff));
        assertFalse(QueueCommands.allowed(sub, staff, "resume"));
    }

    @Test
    void theConsoleGetsStaffActionsButNoPlayerActions() {
        CommandSource console = mock(CommandSource.class);
        when(console.hasPermission(anyString())).thenReturn(true);
        assertEquals(List.of("list", "pause", "resume", "clear", "send", "help"),
                QueueCommands.visible(QueueSettings.SUB_COMMANDS, console));
    }

    @Test
    void suggestionsMatchThePrefix() {
        assertEquals(List.of("bedwars", "bw-1"), QueueCommands.filter(List.of("skywars", "bw-1", "bedwars"), "B"));
        assertTrue(QueueCommands.filter(List.of("skywars"), "x").isEmpty());
    }
}
