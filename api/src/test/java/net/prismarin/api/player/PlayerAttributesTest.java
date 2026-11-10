package net.prismarin.api.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.prismarin.api.event.Subscription;
import org.junit.jupiter.api.Test;

class PlayerAttributesTest {

    private final NetworkPlayer target = new NetworkPlayer(UUID.randomUUID(), "Leon", "", Locale.GERMAN, "bw-1",
            "arena", true, false, null, null, 3, 0L, NetworkPlayer.Source.LOCAL);

    @Test
    void registeredAttributesAnswerInOrder() {
        PlayerAttributes attributes = new PlayerAttributes();
        attributes.register(PlayerAttribute.of("wins", player -> "12"));
        attributes.register(new Line("kills", -1, "Kills: 5"));
        attributes.register(PlayerAttribute.of("empty", player -> ""));
        assertEquals(List.of("kills", "empty", "wins"), attributes.all().stream().map(PlayerAttribute::key).toList());
        assertEquals(Map.of("wins", "12", "kills", "5"), attributes.values(null, target));
        assertEquals(Optional.of("12"), attributes.value("WINS", null, target));
        assertEquals(Optional.empty(), attributes.value("empty", null, target));
        assertEquals(Optional.empty(), attributes.value("missing", null, target));
    }

    @Test
    void profileLinesSkipPlacedKeys() {
        PlayerAttributes attributes = new PlayerAttributes();
        attributes.register(new Line("kills", 1, "Kills: 5"));
        attributes.register(new Line("deaths", 2, "Deaths: 1"));
        assertEquals(List.of(Component.text("Kills: 5"), Component.text("Deaths: 1")),
                attributes.profileLines(target.uuid(), target, Locale.GERMAN, Set.of()));
        assertEquals(List.of(Component.text("Deaths: 1")),
                attributes.profileLines(target.uuid(), target, Locale.GERMAN, Set.of("kills")));
        assertEquals(Optional.of(Component.text("Kills: 5")),
                attributes.profileLine("kills", target.uuid(), target, Locale.GERMAN));
    }

    @Test
    void keysAreValidatedAndUnique() {
        PlayerAttributes attributes = new PlayerAttributes();
        Subscription subscription = attributes.register(PlayerAttribute.of("bedwars_wins", player -> "1"));
        assertThrows(IllegalArgumentException.class,
                () -> attributes.register(PlayerAttribute.of("bedwars_wins", player -> "2")));
        assertThrows(IllegalArgumentException.class,
                () -> attributes.register(PlayerAttribute.of("Bed Wars", player -> "2")));
        subscription.unsubscribe();
        assertTrue(attributes.all().isEmpty());
        attributes.register(PlayerAttribute.of("bedwars_wins", player -> "2"));
        assertEquals(Optional.of("2"), attributes.value("bedwars_wins", null, target));
    }

    @Test
    void failingProvidersAreIsolated() {
        PlayerAttributes attributes = new PlayerAttributes();
        attributes.register(PlayerAttribute.of("broken", player -> {
            throw new IllegalStateException("boom");
        }));
        attributes.register(PlayerAttribute.of("fine", player -> "ok"));
        assertEquals(Map.of("fine", "ok"), attributes.values(null, target));
        assertEquals(Optional.empty(), attributes.value("broken", null, target));
    }

    private record Line(String key, int order, String line) implements PlayerAttribute {

        @Override
        public Optional<String> value(UUID viewer, NetworkPlayer target) {
            return Optional.of(line.substring(line.indexOf(':') + 2));
        }

        @Override
        public Optional<Component> profileLine(UUID viewer, NetworkPlayer target, Locale locale) {
            return Optional.of(Component.text(line));
        }
    }
}
