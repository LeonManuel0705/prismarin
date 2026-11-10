package net.prismarin.shared.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PlayerWireTest {

    private final UUID leon = UUID.randomUUID();

    @Test
    void liveEntriesSurviveAnAnswer() {
        PlayerWire.Live live = new PlayerWire.Live(leon, "Leon", "SilentFox", "bw-1", "arena", "de", true);
        JsonObject answer = PlayerWire.answer(7, List.of(live));
        JsonObject parsed = JsonParser.parseString(answer.toString()).getAsJsonObject();
        assertEquals(7, PlayerWire.id(parsed));
        assertEquals(List.of(live), PlayerWire.players(parsed));
    }

    @Test
    void brokenEntriesAreSkipped() {
        JsonObject answer = new JsonObject();
        JsonArray players = new JsonArray();
        players.add("nope");
        JsonObject noUuid = new JsonObject();
        noUuid.addProperty("name", "Leon");
        players.add(noUuid);
        JsonObject minimal = new JsonObject();
        minimal.addProperty("uuid", leon.toString());
        players.add(minimal);
        answer.add("players", players);
        answer.addProperty("id", "x");
        List<PlayerWire.Live> parsed = PlayerWire.players(answer);
        assertEquals(1, parsed.size());
        assertEquals("", parsed.getFirst().name());
        assertFalse(parsed.getFirst().vanished());
        assertEquals(0, PlayerWire.id(answer));
        assertEquals(List.of(), PlayerWire.players(null));
    }

    @Test
    void queriesAreDedupedAndCapped() {
        List<UUID> many = new ArrayList<>();
        IntStream.range(0, PlayerWire.MAX_PLAYERS + 20).forEach(index -> many.add(UUID.randomUUID()));
        many.add(many.getFirst());
        List<String> names = new ArrayList<>(List.of(" Leon ", "leon", "", "Tim"));
        IntStream.range(0, 20).forEach(index -> names.add("Name" + index));
        PlayerWire.Query query = new PlayerWire.Query(3, many, names);
        assertEquals(PlayerWire.MAX_PLAYERS, query.players().size());
        assertEquals(PlayerWire.MAX_NAMES, query.names().size());
        assertEquals(List.of("Leon", "Tim"), query.names().subList(0, 2));
        PlayerWire.Query parsed = PlayerWire.Query.fromJson(query.toJson()).orElseThrow();
        assertEquals(query, parsed);
        assertTrue(PlayerWire.Query.fromJson(null).isEmpty());
    }

    @Test
    void statesCarryWorldAndNick() {
        PlayerWire.State state = new PlayerWire.State("arena", "SilentFox");
        assertEquals(state, PlayerWire.State.fromJson(state.toJson()));
        assertEquals(new PlayerWire.State("", ""), PlayerWire.State.fromJson(new JsonObject()));
        assertEquals(Optional.empty(), PlayerWire.Live.fromJson(null));
    }
}
