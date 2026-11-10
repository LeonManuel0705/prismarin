package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.command.CommandSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.velocity.social.Page;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PunishmentListsTest {

    private static List<Punishment> bans(int count) {
        List<Punishment> bans = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            bans.add(new Punishment("id" + i, UUID.randomUUID(), "Player" + i, PunishmentType.BAN, "Hacking",
                    "Leon", 1_000L * (count - i), 0L, true, "", 0L));
        }
        return bans;
    }

    @Test
    void pagesAreOneBasedAndClamped() {
        List<Punishment> bans = bans(19);
        Page<Punishment> first = PunishmentLists.page(bans, 1);
        assertEquals(0, first.page());
        assertEquals(3, first.pages());
        assertEquals(PunishmentLists.PAGE_SIZE, first.entries().size());
        assertEquals("id0", first.entries().getFirst().id());
        Page<Punishment> last = PunishmentLists.page(bans, 3);
        assertEquals(3, last.entries().size());
        assertEquals("id16", last.entries().getFirst().id());
        assertEquals(2, PunishmentLists.page(bans, 99).page());
        assertEquals(0, PunishmentLists.page(bans, 0).page());
        assertEquals(0, PunishmentLists.page(bans, -5).page());
        Page<Punishment> none = PunishmentLists.page(List.of(), 2);
        assertEquals(1, none.pages());
        assertTrue(none.entries().isEmpty());
    }

    @Test
    void nickedPlayersKeepTheirNickForEveryoneElse() {
        assertEquals("SilentFox", PunishmentLists.publicName("Steve", Optional.of("SilentFox")));
        assertEquals("Steve", PunishmentLists.publicName("Steve", Optional.empty()));
        assertEquals("Steve", PunishmentLists.publicName("Steve", Optional.of(" ")));
    }

    @Test
    void listsNeedOneOfTheirPermissions() {
        CommandSource nobody = Mockito.mock(CommandSource.class);
        assertFalse(PunishmentLists.allowed(nobody, PunishmentType.BAN));
        CommandSource banner = Mockito.mock(CommandSource.class);
        Mockito.when(banner.hasPermission("prismarin.staff.ban")).thenReturn(true);
        assertTrue(PunishmentLists.allowed(banner, PunishmentType.BAN));
        assertFalse(PunishmentLists.allowed(banner, PunishmentType.MUTE));
        CommandSource visitor = Mockito.mock(CommandSource.class);
        Mockito.when(visitor.hasPermission("prismarin.mutelist")).thenReturn(true);
        assertTrue(PunishmentLists.allowed(visitor, PunishmentType.MUTE));
        CommandSource historian = Mockito.mock(CommandSource.class);
        Mockito.when(historian.hasPermission("prismarin.staff.history")).thenReturn(true);
        assertTrue(PunishmentLists.allowed(historian, PunishmentType.BAN));
        assertTrue(PunishmentLists.allowed(historian, PunishmentType.MUTE));
    }
}
