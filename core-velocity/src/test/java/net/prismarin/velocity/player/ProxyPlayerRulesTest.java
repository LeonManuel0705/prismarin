package net.prismarin.velocity.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.prismarin.api.player.NetworkPlayer;
import net.prismarin.shared.player.PlayerWire;
import org.junit.jupiter.api.Test;

class ProxyPlayerRulesTest {

    private final UUID leon = UUID.randomUUID();
    private final NetworkPlayer nicked = new NetworkPlayer(leon, "Leon", "SilentFox", Locale.GERMAN, "bw-1",
            "arena", true, true, null, null, 2, 0L, NetworkPlayer.Source.LOCAL);

    @Test
    void namesMatchTheRealNameOrTheShownNick() {
        assertTrue(ProxyPlayerService.matches(nicked, "leon"));
        assertTrue(ProxyPlayerService.matches(nicked, "SILENTFOX"));
        assertFalse(ProxyPlayerService.matches(nicked, "Tim"));
        NetworkPlayer plain = new NetworkPlayer(UUID.randomUUID(), "Tim", "", Locale.GERMAN, "lobby", "", true,
                false, null, null, 0, 0L, NetworkPlayer.Source.LOCAL);
        assertFalse(ProxyPlayerService.matches(plain, ""));
    }

    @Test
    void liveEntriesCarryEverythingTheBackendNeeds() {
        PlayerWire.Live live = ProxyPlayerService.live(nicked);
        assertEquals(new PlayerWire.Live(leon, "Leon", "SilentFox", "bw-1", "arena", "de", true), live);
    }

    @Test
    void nickClaimsAreParsedLeniently() {
        UUID other = UUID.randomUUID();
        Map<UUID, String> claims = ProxyNickClaims.parse(List.of(
                "{\"uuid\":\"" + leon + "\",\"name\":\"SilentFox\",\"server\":\"bw-1\"}",
                "{\"uuid\":\"" + other + "\",\"name\":\" \"}",
                "{\"uuid\":\"broken\",\"name\":\"Fox\"}",
                "{\"name\":\"Fox\"}",
                "[]",
                "not json"));
        assertEquals(Map.of(leon, "SilentFox"), claims);
    }
}
