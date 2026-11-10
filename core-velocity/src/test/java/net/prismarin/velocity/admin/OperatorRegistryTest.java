package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.permission.Tristate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperatorRegistryTest {

    @Test
    void operatorsOnlyGainPrismarinPermissions() {
        assertEquals(Tristate.TRUE, OperatorRegistry.decide("prismarin.staff.ban", true, Tristate.UNDEFINED));
        assertEquals(Tristate.UNDEFINED, OperatorRegistry.decide("velocity.command.server", true, Tristate.UNDEFINED));
        assertEquals(Tristate.FALSE, OperatorRegistry.decide("prismarin.staff.ban", false, Tristate.FALSE));
    }

    @Test
    void updatesTrackOperatorStatus() {
        OperatorRegistry registry = new OperatorRegistry();
        UUID player = UUID.randomUUID();
        registry.update(player, true);
        assertTrue(registry.operator(player));
        registry.update(player, false);
        assertFalse(registry.operator(player));
        assertEquals(0, registry.count());
    }
}
