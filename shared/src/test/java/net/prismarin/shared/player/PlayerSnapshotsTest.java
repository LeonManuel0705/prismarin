package net.prismarin.shared.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.player.NetworkPlayer;
import net.prismarin.api.profile.PlayerProfile;
import org.junit.jupiter.api.Test;

class PlayerSnapshotsTest {

    private final UUID uuid = UUID.randomUUID();
    private final PlayerProfile profile = new PlayerProfile(uuid, "Leon", Instant.ofEpochMilli(1_000L),
            Instant.ofEpochMilli(5_000L), "en", Map.of(), 7, 0L, Map.of());

    @Test
    void localesFallBackForBlankOrBrokenTags() {
        assertEquals(Locale.GERMAN, PlayerSnapshots.locale("", Locale.GERMAN));
        assertEquals(Locale.GERMAN, PlayerSnapshots.locale((String) null, Locale.GERMAN));
        assertEquals(Locale.forLanguageTag("pt-BR"), PlayerSnapshots.locale("pt_BR", Locale.GERMAN));
        assertEquals(Locale.GERMAN, PlayerSnapshots.locale("!!", Locale.GERMAN));
    }

    @Test
    void offlineSnapshotsComeFromTheProfile() {
        NetworkPlayer player = PlayerSnapshots.offline(profile, Locale.GERMAN);
        assertEquals("Leon", player.realName());
        assertEquals(Locale.ENGLISH, player.locale());
        assertEquals(7, player.level());
        assertEquals(5_000L, player.lastSeen());
        assertFalse(player.online());
        assertEquals(NetworkPlayer.Source.STORAGE, player.source());
    }

    @Test
    void liveSnapshotsPreferTheProxyAndFillGapsFromTheProfile() {
        PlayerWire.Live live = new PlayerWire.Live(uuid, "", "SilentFox", "bw-1", "arena", "", false);
        NetworkPlayer player = PlayerSnapshots.live(live, Optional.of(profile), Locale.GERMAN, 9L,
                NetworkPlayer.Source.PROXY);
        assertEquals("Leon", player.realName());
        assertEquals("SilentFox", player.shownName());
        assertEquals(Locale.ENGLISH, player.locale());
        assertEquals("arena", player.world());
        assertEquals(7, player.level());
        assertTrue(player.online());
        assertEquals(9L, player.lastSeen());
        NetworkPlayer german = PlayerSnapshots.live(new PlayerWire.Live(uuid, "Leon", "", "bw-1", "", "de", true),
                Optional.empty(), Locale.ENGLISH, 9L, NetworkPlayer.Source.PROXY);
        assertEquals(Locale.GERMAN, german.locale());
        assertTrue(german.vanished());
        assertEquals(0, german.level());
    }

    @Test
    void storedSnapshotsUsePresenceAndShowNicksOnlyWhileOnline() {
        PlayerSnapshots.StoredPresence online = PlayerSnapshots.StoredPresence
                .fromJson("{\"name\":\"Leon\",\"server\":\"bw-1\",\"online\":true,\"last-seen\":8000}")
                .orElseThrow();
        NetworkPlayer player = PlayerSnapshots.stored(uuid, Optional.of(online), Optional.of("SilentFox"),
                Optional.of(profile), Locale.GERMAN).orElseThrow();
        assertTrue(player.online());
        assertEquals("bw-1", player.server());
        assertEquals("SilentFox", player.shownName());
        assertEquals(8_000L, player.lastSeen());
        PlayerSnapshots.StoredPresence offline = new PlayerSnapshots.StoredPresence("Leon", "", false, 0L);
        NetworkPlayer gone = PlayerSnapshots.stored(uuid, Optional.of(offline), Optional.of("SilentFox"),
                Optional.empty(), Locale.GERMAN).orElseThrow();
        assertFalse(gone.nicked());
        assertEquals(Locale.GERMAN, gone.locale());
        assertTrue(PlayerSnapshots.stored(uuid, Optional.empty(), Optional.empty(), Optional.empty(),
                Locale.GERMAN).isEmpty());
        assertTrue(PlayerSnapshots.StoredPresence.fromJson("[]").isEmpty());
        assertTrue(PlayerSnapshots.StoredPresence.fromJson("{\"server\":\"x\"}").isEmpty());
        assertTrue(PlayerSnapshots.StoredPresence.fromJson("not json").isEmpty());
    }
}
