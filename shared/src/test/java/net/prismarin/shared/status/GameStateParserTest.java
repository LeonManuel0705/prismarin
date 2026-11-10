package net.prismarin.shared.status;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import net.prismarin.api.server.GameState;
import org.junit.jupiter.api.Test;

class GameStateParserTest {

    @Test
    void defaultWordsAreRecognised() {
        assertEquals(GameState.WAITING, GameStateParser.parse("Waiting for players", GameStateParser.DEFAULT_MAPPING));
        assertEquals(GameState.RUNNING, GameStateParser.parse("§cIngame", GameStateParser.DEFAULT_MAPPING));
        assertEquals(GameState.STARTING, GameStateParser.parse("Starting in 5", GameStateParser.DEFAULT_MAPPING));
        assertEquals(GameState.RESTARTING, GameStateParser.parse("Restarting", GameStateParser.DEFAULT_MAPPING));
        assertEquals(GameState.UNKNOWN, GameStateParser.parse("A Minecraft Server", GameStateParser.DEFAULT_MAPPING));
    }

    @Test
    void customMappingWins() {
        Map<GameState, List<String>> mapping = Map.of(GameState.RUNNING, List.of("kampf"));
        assertEquals(GameState.RUNNING, GameStateParser.parse("KAMPF läuft", mapping));
    }

    @Test
    void prismarinMotdRoundTrips() {
        String motd = GameStateParser.motd(GameState.STARTING, "Map: Castle");
        GameStateParser.Parsed parsed = GameStateParser.parseMotd(motd);
        assertEquals(GameState.STARTING, parsed.state());
        assertEquals("Map: Castle", parsed.text());
    }

    @Test
    void foreignMotdFallsBackToWords() {
        assertEquals(GameState.WAITING, GameStateParser.parseMotd("  Waiting  ").state());
        assertEquals(GameState.UNKNOWN, GameStateParser.parseMotd("Lobby").state());
        assertEquals(GameState.UNKNOWN, GameStateParser.parseMotd(null).state());
        assertEquals(GameState.UNKNOWN, GameStateParser.parseMotd("prismarin:NONSENSE").state());
    }
}
