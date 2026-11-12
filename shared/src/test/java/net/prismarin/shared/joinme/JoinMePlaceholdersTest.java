package net.prismarin.shared.joinme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeAudience;
import net.prismarin.api.server.GameState;
import org.junit.jupiter.api.Test;

class JoinMePlaceholdersTest {

    private final JoinMe joinMe = new JoinMe("k3f9a2", UUID.randomUUID(), "Lea", "Shadow", "bedwars-2", "bedwars",
            "arena-1", GameState.WAITING, "bedwars", Map.of("map", "Castle", "creator", "Hacker", "players", "3",
            "max_players", "8", "group", "<red>fake</red>"), JoinMeAudience.NETWORK, 8, 2, 0L, 125_000L);

    @Test
    void standardValuesWinOverValuesFromIntegrations() {
        Map<String, String> values = JoinMePlaceholders.values(joinMe, false, 0L);
        assertEquals("Shadow", values.get("creator"));
        assertEquals("Castle", values.get("map"));
        assertEquals("bedwars-2", values.get("server"));
        assertEquals("bedwars", values.get("group_id"));
        assertEquals("arena-1", values.get("world"));
        assertEquals("2", values.get("joins"));
        assertEquals("8", values.get("max_joins"));
        assertEquals("6", values.get("joins_left"));
        assertEquals("2:05", values.get("time"));
        assertEquals("125", values.get("seconds"));
        assertEquals("3", values.get("players"));
        assertEquals("3", values.get("world_players"));
        assertEquals(null, values.get("group"));
        assertEquals("Lea", JoinMePlaceholders.values(joinMe, true, 0L).get("creator"));
    }

    @Test
    void liveServerNumbersReplaceTheSnapshot() {
        Map<String, String> values = JoinMePlaceholders.withStatus(JoinMePlaceholders.values(joinMe, false, 0L), 5,
                16);
        assertEquals("5", values.get("players"));
        assertEquals("16", values.get("max_players"));
        Map<String, String> unknown = JoinMePlaceholders.withStatus(values, -1, 0);
        assertEquals("5", unknown.get("players"));
        JoinMe bare = joinMe.withJoins(0);
        JoinMe unlimited = new JoinMe("k3f9a2", bare.creator(), "Lea", "", "s", "g", "", GameState.UNKNOWN, "",
                Map.of(), JoinMeAudience.NETWORK, 0, 0, 0L, 0L);
        Map<String, String> defaults = JoinMePlaceholders.values(unlimited, false, 10L);
        assertEquals("?", defaults.get("players"));
        assertEquals("-", defaults.get("max_joins"));
        assertEquals("-", defaults.get("joins_left"));
        assertEquals("0:00", defaults.get("time"));
    }

    @Test
    void theResolverFillsPlainAndFormattedValues() {
        Map<String, String> values = JoinMePlaceholders.values(joinMe, false, 0L);
        Component text = MiniMessage.miniMessage().deserialize(
                "<creator> plays <group> on <server> (<map>, <players>/<max_players>, <time>)",
                JoinMePlaceholders.resolver(values, Map.of("group", Component.text("BedWars"))));
        assertEquals("Shadow plays BedWars on bedwars-2 (Castle, 3/8, 2:05)",
                PlainTextComponentSerializer.plainText().serialize(text));
        Component unsafe = MiniMessage.miniMessage().deserialize("<map>", JoinMePlaceholders.resolver(
                Map.of("map", "<red>evil</red>"), Map.of()));
        assertEquals("<red>evil</red>", PlainTextComponentSerializer.plainText().serialize(unsafe));
    }

    @Test
    void theClockShowsMinutesAndHours() {
        assertEquals("0:01", JoinMePlaceholders.clock(1L));
        assertEquals("1:00", JoinMePlaceholders.clock(60_000L));
        assertEquals("1:00:05", JoinMePlaceholders.clock(3_605_000L));
        assertEquals("0:00", JoinMePlaceholders.clock(-5L));
    }
}
