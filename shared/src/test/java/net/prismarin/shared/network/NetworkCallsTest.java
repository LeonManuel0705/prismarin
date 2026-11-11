package net.prismarin.shared.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NetworkCallsTest {

    @Test
    void requestsSurviveTheWire() {
        JsonObject body = new JsonObject();
        body.addProperty("actor", "AntiCheat");
        JsonObject message = NetworkCalls.request(7, NetworkCalls.Action.AUDIT_RECORD, body);

        NetworkCalls.Request parsed = NetworkCalls.parseRequest(reparse(message)).orElseThrow();

        assertEquals(7, parsed.id());
        assertEquals(NetworkCalls.Action.AUDIT_RECORD, parsed.action());
        assertEquals("AntiCheat", parsed.body().get("actor").getAsString());
        assertEquals("audit-record", message.get("action").getAsString());
    }

    @Test
    void brokenRequestsAreRejected() {
        assertTrue(NetworkCalls.parseRequest(null).isEmpty());
        assertTrue(NetworkCalls.parseRequest(raw("{\"action\":\"audit-record\"}")).isEmpty());
        assertTrue(NetworkCalls.parseRequest(raw("{\"id\":0,\"action\":\"audit-record\"}")).isEmpty());
        assertTrue(NetworkCalls.parseRequest(raw("{\"id\":-3,\"action\":\"audit-record\"}")).isEmpty());
        assertTrue(NetworkCalls.parseRequest(raw("{\"id\":\"7\",\"action\":\"audit-record\"}")).isEmpty());
        assertTrue(NetworkCalls.parseRequest(raw("{\"id\":7,\"action\":\"netexec\"}")).isEmpty());
        assertTrue(NetworkCalls.parseRequest(raw("{\"id\":7,\"action\":\"audit-record\",\"body\":[]}")).isEmpty());
        JsonObject huge = NetworkCalls.request(7, NetworkCalls.Action.STAFF_ALERT, new JsonObject());
        huge.addProperty("padding", "x".repeat(NetworkCalls.MAX_BYTES));
        assertTrue(NetworkCalls.parseRequest(huge).isEmpty());
        assertEquals(Optional.of(new JsonObject()), NetworkCalls.parseRequest(
                raw("{\"id\":7,\"action\":\"maintenance-state\"}")).map(NetworkCalls.Request::body));
    }

    @Test
    void answersCarryBodiesOrErrors() {
        JsonObject body = new JsonObject();
        body.addProperty("changed", true);
        NetworkCalls.Answer ok = NetworkCalls.parseAnswer(reparse(NetworkCalls.answer(3, body))).orElseThrow();
        NetworkCalls.Answer failed = NetworkCalls.parseAnswer(reparse(NetworkCalls.failure(4, "nope"))).orElseThrow();

        assertTrue(ok.ok());
        assertTrue(ok.body().get("changed").getAsBoolean());
        assertFalse(failed.ok());
        assertEquals("nope", failed.error());
        assertEquals(NetworkCalls.MAX_ERROR, NetworkCalls.failure(5, "x".repeat(500)).get("error").getAsString()
                .length());
        assertEquals("failed", NetworkCalls.failure(6, " ").get("error").getAsString());
        assertTrue(NetworkCalls.parseAnswer(raw("{\"ok\":true}")).isEmpty());
        assertFalse(NetworkCalls.parseAnswer(raw("{\"id\":2,\"ok\":\"true\"}")).orElseThrow().ok());
    }

    @Test
    void answersThatDoNotFitBecomeFailures() {
        JsonObject body = new JsonObject();
        body.addProperty("text", "x".repeat(NetworkCalls.MAX_BYTES));

        NetworkCalls.Answer answer = NetworkCalls.parseAnswer(NetworkCalls.answer(9, body)).orElseThrow();

        assertFalse(answer.ok());
        assertEquals("the answer is too large", answer.error());
    }

    @Test
    void listsAreCutToTheBudgetInOrder() {
        JsonArray items = new JsonArray();
        for (int index = 0; index < 100; index++) {
            items.add("item-" + index + "-" + "x".repeat(90));
        }

        JsonArray kept = NetworkCalls.within(items, 1_000);

        assertTrue(kept.size() > 0 && kept.size() < items.size());
        assertTrue(NetworkCalls.bytes(kept) <= 1_000);
        assertEquals(items.get(0), kept.get(0));
        assertEquals(items, NetworkCalls.within(items, Integer.MAX_VALUE));
    }

    @Test
    void actionIdsAreUniqueAndResolvable() {
        Set<String> ids = Arrays.stream(NetworkCalls.Action.values()).map(NetworkCalls.Action::id)
                .collect(Collectors.toSet());
        assertEquals(NetworkCalls.Action.values().length, ids.size());
        for (NetworkCalls.Action action : NetworkCalls.Action.values()) {
            assertEquals(Optional.of(action), NetworkCalls.Action.byId(action.id()));
        }
        assertEquals(Optional.empty(), NetworkCalls.Action.byId("AUDIT_RECORD"));
        assertEquals(Optional.empty(), NetworkCalls.Action.byId(null));
    }

    private static JsonObject reparse(JsonObject message) {
        return raw(message.toString());
    }

    private static JsonObject raw(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
