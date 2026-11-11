package net.prismarin.lobby.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.prismarin.lobby.action.ItemAction;
import net.prismarin.lobby.api.LobbyEnteredEvent;
import net.prismarin.lobby.api.LobbyInfo;
import net.prismarin.lobby.api.LobbyLeftEvent;
import net.prismarin.lobby.api.LobbyMenu;
import org.junit.jupiter.api.Test;

class LobbyModelTest {

    @Test
    void lobbyInfoNormalisesItsFields() {
        LobbyInfo world = new LobbyInfo("main", null, LobbyInfo.Kind.WORLD, "lobby", null, -5);
        LobbyInfo premium = new LobbyInfo("gold", "Gold", LobbyInfo.Kind.SERVER, "lobby-2", " vip ", 40);

        assertEquals("main", world.displayName());
        assertEquals("", world.permission());
        assertEquals(0, world.softMaxPlayers());
        assertFalse(world.premium());
        assertTrue(premium.premium());
        assertEquals("vip", premium.permission());
        assertThrows(NullPointerException.class, () -> new LobbyInfo(null, "", LobbyInfo.Kind.WORLD, "w", "", 0));
    }

    @Test
    void lobbyEventsDefaultTheOtherWorld() {
        UUID player = UUID.randomUUID();

        assertEquals("", new LobbyEnteredEvent(player, "lobby", null, LobbyEnteredEvent.Cause.JOIN).previousWorld());
        assertEquals("", new LobbyLeftEvent(player, "lobby", null, LobbyLeftEvent.Cause.QUIT).nextWorld());
        assertThrows(NullPointerException.class,
                () -> new LobbyEnteredEvent(player, null, "", LobbyEnteredEvent.Cause.WORLD_CHANGE));
    }

    @Test
    void everyLobbyMenuIsAKnownMenuAction() {
        Set<String> ids = Arrays.stream(LobbyMenu.values()).map(LobbyMenu::id).collect(Collectors.toSet());

        assertEquals(Set.of(ItemAction.MENU_NAVIGATOR, ItemAction.MENU_PROFILE, ItemAction.MENU_LOBBIES,
                ItemAction.MENU_LANGUAGES, ItemAction.MENU_FRIENDS, ItemAction.MENU_PARTY, ItemAction.MENU_NICK), ids);
    }
}
