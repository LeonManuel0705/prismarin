package net.prismarin.api.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PunishmentTest {

    private final UUID player = UUID.randomUUID();

    @Test
    void theOldConstructorFillsTheNewFields() {
        Punishment punishment = new Punishment("a", player, null, PunishmentType.BAN, null, null, 10L, 0L, true,
                null, 0L);
        assertEquals("", punishment.template());
        assertEquals("", punishment.addressHash());
        assertEquals("", punishment.replacedBy());
        assertTrue(punishment.permanent());
        assertEquals(0L, punishment.length());
        assertTrue(punishment.inForce(1_000L));
    }

    @Test
    void liftingAndReplacingAreTold() {
        Punishment punishment = new Punishment("a", player, "Steve", PunishmentType.MUTE, "spam", "Leon", 10L, 110L,
                true, "", 0L, "chat", "", "", "", "");
        assertEquals(100L, punishment.length());
        Punishment lifted = punishment.revoke("Mod", 50L, "mistake");
        assertTrue(lifted.lifted());
        assertFalse(lifted.replaced());
        assertEquals("mistake", lifted.revokeReason());
        Punishment replaced = punishment.replace("Mod", 50L, "b");
        assertFalse(replaced.lifted());
        assertTrue(replaced.replaced());
        assertFalse(replaced.inForce(60L));
        assertEquals("chat", replaced.template());
    }

    @Test
    void requestsDropEmptyDurations() {
        PunishmentRequest request = PunishmentRequest.of(player, "Steve", PunishmentType.BAN, "x", "Leon",
                Duration.ZERO);
        assertNull(request.duration());
        PunishmentRequest templated = request.withTemplate("hacking", "fly").withAddress("ff");
        assertEquals("hacking", templated.template());
        assertEquals("fly", templated.note());
        assertEquals("ff", templated.addressHash());
    }
}
