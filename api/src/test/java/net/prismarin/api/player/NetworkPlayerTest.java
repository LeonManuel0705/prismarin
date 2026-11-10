package net.prismarin.api.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

class NetworkPlayerTest {

    private final UUID uuid = UUID.randomUUID();

    @Test
    void onlinePlayersKeepServerWorldAndNick() {
        NetworkPlayer player = new NetworkPlayer(uuid, "Leon", " SilentFox ", Locale.GERMAN, "bw-1", "arena", true,
                true, null, null, 4, 10L, NetworkPlayer.Source.LOCAL);
        assertTrue(player.nicked());
        assertEquals("SilentFox", player.shownName());
        assertEquals(Optional.of("SilentFox"), player.nickName());
        assertEquals(Optional.of("bw-1"), player.currentServer());
        assertEquals(Optional.of("arena"), player.currentWorld());
        assertTrue(player.onServer("BW-1"));
        assertTrue(player.vanished());
        assertTrue(player.live());
        assertEquals(RankDisplay.NONE, player.rank());
        assertEquals(RankDisplay.NONE, player.realRank());
    }

    @Test
    void offlinePlayersCarryNoLiveState() {
        NetworkPlayer player = new NetworkPlayer(uuid, null, "SilentFox", null, "bw-1", "arena", false, true, null,
                null, -2, 10L, NetworkPlayer.Source.STORAGE);
        assertFalse(player.nicked());
        assertEquals("", player.server());
        assertEquals("", player.world());
        assertFalse(player.vanished());
        assertEquals(Locale.ROOT, player.locale());
        assertEquals("", player.realName());
        assertEquals(0, player.level());
        assertFalse(player.live());
        assertFalse(player.onServer("bw-1"));
    }

    @Test
    void asOfflineHidesTheNickAndShowsTheRealRank() {
        RankDisplay admin = new RankDisplay(" Admin ", Component.text("Admin"), null, null, null, 100);
        NetworkPlayer player = new NetworkPlayer(uuid, "Leon", "SilentFox", Locale.ENGLISH, "bw-1", "arena", true,
                false, RankDisplay.NONE, admin, 4, 10L, NetworkPlayer.Source.PROXY);
        NetworkPlayer offline = player.asOffline();
        assertFalse(offline.online());
        assertEquals("Leon", offline.shownName());
        assertEquals(admin, offline.rank());
        assertEquals("Admin", admin.label());
        assertEquals(NamedTextColor.WHITE, admin.nameColor());
        assertTrue(admin.known());
        assertFalse(RankDisplay.NONE.known());
        assertEquals(NamedTextColor.GRAY, admin.chatColorOr(NamedTextColor.GRAY));
        assertEquals(4, offline.level());
    }

    @Test
    void eventsNormalizeTheirValues() {
        ServerChangedEvent joined = new ServerChangedEvent(uuid, null, "lobby");
        assertTrue(joined.joinedNetwork());
        assertFalse(new ServerChangedEvent(uuid, "lobby", "bw-1").joinedNetwork());
        assertThrows(NullPointerException.class, () -> new LocaleChangedEvent(uuid, null, Locale.GERMAN));
    }
}
