package net.prismarin.velocity.locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerLocaleRegistryTest {

    @Test
    void listenersHearRealChangesOnly() {
        PlayerLocaleRegistry registry = new PlayerLocaleRegistry();
        UUID player = UUID.randomUUID();
        List<String> changes = new ArrayList<>();
        registry.onChange((uuid, previous, current) -> changes.add(previous.toLanguageTag() + ">"
                + current.toLanguageTag()));
        registry.update(player, "de", "lobby");
        registry.update(player, "de", "bw-1");
        registry.update(player, "en_US", "bw-1");
        registry.update(player, " ", "bw-1");
        assertEquals(List.of("de>en-US"), changes);
        assertEquals(Optional.of(Locale.forLanguageTag("en-US")), registry.locale(player));
    }
}
