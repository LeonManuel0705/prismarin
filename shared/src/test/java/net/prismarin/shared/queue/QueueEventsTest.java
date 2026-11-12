package net.prismarin.shared.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.queue.QueueJoinedEvent;
import net.prismarin.api.queue.QueueLeftEvent;
import net.prismarin.api.queue.QueuePausedEvent;
import net.prismarin.api.queue.QueueSendEvent;
import net.prismarin.api.queue.QueueSentEvent;
import net.prismarin.api.queue.QueueTarget;
import net.prismarin.shared.network.NetworkEvents;
import org.junit.jupiter.api.Test;

class QueueEventsTest {

    private static final QueueTarget BEDWARS = QueueTarget.group("bedwars");

    private final UUID player = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();

    private PrismarinEvent roundTrip(PrismarinEvent event) {
        JsonObject message = NetworkEvents.encode(event).orElseThrow();
        return NetworkEvents.decode(message).orElseThrow();
    }

    @Test
    void everyQueueEventReachesTheBackendsUnchanged() {
        List<PrismarinEvent> events = List.of(
                new QueueJoinedEvent(BEDWARS, player, List.of(member), 3, 9),
                new QueueLeftEvent(QueueTarget.server("bw-1"), player, List.of(), QueueLeftEvent.Reason.SWITCHED_SERVER,
                        2),
                new QueueSentEvent(BEDWARS, "bw-2", player, List.of(member), 42_000L, 1),
                new QueuePausedEvent(BEDWARS, true, "Leon"));
        for (PrismarinEvent event : events) {
            assertEquals(event, roundTrip(event));
        }
    }

    @Test
    void everyLeaveReasonSurvivesTheTrip() {
        for (QueueLeftEvent.Reason reason : QueueLeftEvent.Reason.values()) {
            QueueLeftEvent event = new QueueLeftEvent(BEDWARS, player, List.of(member), reason, 0);
            assertEquals(reason, ((QueueLeftEvent) roundTrip(event)).reason());
            assertEquals(reason, QueueLeftEvent.Reason.byId(reason.id()).orElseThrow());
        }
    }

    @Test
    void cancellableProxyEventsStayOnTheProxyAndBrokenMessagesAreIgnored() {
        assertTrue(QueueEvents.encode(new QueueSendEvent(BEDWARS, "bw-1", player, List.of())).isEmpty());
        JsonObject broken = new JsonObject();
        broken.addProperty("event", "queue-joined");
        broken.addProperty("target", "nowhere");
        assertTrue(QueueEvents.decode(broken).isEmpty());
        JsonObject other = new JsonObject();
        other.addProperty("event", "something-else");
        assertTrue(QueueEvents.decode(other).isEmpty());
    }
}
