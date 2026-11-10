package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.PunishmentRequest;
import net.prismarin.api.moderation.PunishmentType;
import org.junit.jupiter.api.Test;

class PunishmentRulesTest {

    private final UUID player = UUID.randomUUID();

    private Punishment ban(long expiresAt, String address) {
        return new Punishment("a", player, "Steve", PunishmentType.BAN, "x", "Leon", 0L, expiresAt, true, "", 0L,
                "", "", address, "", "");
    }

    private PunishmentRequest request(Duration duration, String address) {
        return PunishmentRequest.of(player, "Steve", PunishmentType.BAN, "y", "Mod", duration).withAddress(address);
    }

    @Test
    void shorterPunishmentsDoNotReplaceLongerOnes() {
        long now = System.currentTimeMillis();
        assertTrue(PunishmentFlow.outlasts(ban(0L, ""), request(Duration.ofDays(30), "")));
        assertFalse(PunishmentFlow.outlasts(ban(0L, ""), request(null, "")));
        assertTrue(PunishmentFlow.outlasts(ban(now + Duration.ofDays(60).toMillis(), ""),
                request(Duration.ofDays(30), "")));
        assertFalse(PunishmentFlow.outlasts(ban(now + Duration.ofDays(10).toMillis(), ""),
                request(Duration.ofDays(30), "")));
        assertTrue(PunishmentFlow.outlasts(ban(0L, "hash"), request(null, "")));
        assertFalse(PunishmentFlow.outlasts(ban(0L, "hash"), request(null, "other")));
    }

    @Test
    void historyEntriesTellTheirState() {
        long now = 1_000L;
        Punishment active = new Punishment("a", player, "Steve", PunishmentType.MUTE, "x", "Leon", 0L, 2_000L, true,
                "", 0L);
        assertEquals("entry-active", PunishmentFlow.state(active, now));
        assertEquals("entry-expired", PunishmentFlow.state(active, 5_000L));
        assertEquals("entry-lifted", PunishmentFlow.state(active.revoke("Mod", 500L, ""), now));
        assertEquals("entry-replaced", PunishmentFlow.state(active.replace("Mod", 500L, "b"), now));
        Punishment kick = new Punishment("k", player, "Steve", PunishmentType.KICK, "x", "Leon", 0L, 0L, false,
                "", 0L);
        assertEquals("entry", PunishmentFlow.state(kick, now));
    }

    @Test
    void kindsFollowTypeLengthAndAddress() {
        assertEquals(PunishmentKind.IPBAN, PunishmentKind.of(ban(0L, "hash")));
        assertEquals(PunishmentKind.BAN, PunishmentKind.of(ban(0L, "")));
        assertEquals(PunishmentKind.TEMPBAN, PunishmentKind.of(ban(10L, "")));
        assertEquals(PunishmentKind.TEMPMUTE, PunishmentKind.of(PunishmentType.MUTE, false, false));
        assertEquals("tempmute", PunishmentKind.TEMPMUTE.key());
        assertEquals(PunishmentType.BAN, PunishmentKind.parse(" TempBan ").orElseThrow().type());
        assertTrue(PunishmentKind.parse("jail").isEmpty());
    }
}
