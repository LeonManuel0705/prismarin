package net.prismarin.shared.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class PendingCallsTest {

    private final PendingCalls calls = new PendingCalls(5_000L);

    @Test
    void answersOnlyCompleteCallsOfTheSameRoute() {
        PendingCalls.Ticket ticket = calls.open("carrier-a");
        JsonObject body = new JsonObject();
        body.addProperty("changed", true);

        assertFalse(calls.complete("carrier-b", new NetworkCalls.Answer(ticket.id(), true, "", body)));
        assertFalse(calls.complete("carrier-a", new NetworkCalls.Answer(ticket.id() + 1, true, "", body)));
        assertTrue(calls.complete("carrier-a", new NetworkCalls.Answer(ticket.id(), true, "", body)));
        assertFalse(calls.complete("carrier-a", new NetworkCalls.Answer(ticket.id(), true, "", body)));
        assertEquals(body, ticket.future().join());
        assertEquals(0, calls.size());
    }

    @Test
    void failedAnswersRaiseTheProxyError() {
        PendingCalls.Ticket ticket = calls.open(PendingCalls.BUS);

        calls.complete(PendingCalls.BUS, new NetworkCalls.Answer(ticket.id(), false, "reports are off", null));

        CompletionException error = assertThrows(CompletionException.class, () -> ticket.future().join());
        assertInstanceOf(RemoteCallException.class, error.getCause());
        assertEquals("reports are off", error.getCause().getMessage());
    }

    @Test
    void aLeavingCarrierFailsItsCalls() {
        PendingCalls.Ticket leaving = calls.open("carrier-a");
        PendingCalls.Ticket staying = calls.open("carrier-b");

        calls.forget("carrier-a");

        assertTrue(leaving.future().isCompletedExceptionally());
        assertFalse(staying.future().isDone());
        assertEquals(1, calls.size());
        assertNotEquals(leaving.id(), staying.id());
    }

    @Test
    void unansweredCallsTimeOut() {
        PendingCalls quick = new PendingCalls(20L);
        PendingCalls.Ticket ticket = quick.open("carrier-a");

        CompletionException error = assertThrows(CompletionException.class, () -> ticket.future().join());

        assertInstanceOf(TimeoutException.class, error.getCause());
        assertEquals(0, quick.size());
    }

    @Test
    void failingByIdCompletesTheCall() {
        PendingCalls.Ticket ticket = calls.open("carrier-a");

        calls.fail(ticket.id(), new IllegalArgumentException("too large"));

        assertTrue(ticket.future().isCompletedExceptionally());
        assertEquals(0, calls.size());
    }
}
