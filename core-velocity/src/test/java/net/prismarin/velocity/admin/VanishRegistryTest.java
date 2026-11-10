package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VanishRegistryTest {

    @Test
    void listenersHearOnlyRealChanges() {
        VanishRegistry registry = new VanishRegistry();
        List<String> changes = new ArrayList<>();
        UUID player = UUID.randomUUID();
        registry.onChange((uuid, vanished) -> changes.add(uuid + ":" + vanished));
        registry.update(player, true, 1);
        registry.update(player, true, 3);
        registry.update(player, false, 0);
        registry.update(player, false, 0);
        assertEquals(List.of(player + ":true", player + ":false"), changes);
        assertTrue(!registry.vanished(player));
    }
}
