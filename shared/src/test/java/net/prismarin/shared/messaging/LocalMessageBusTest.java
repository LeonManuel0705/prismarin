package net.prismarin.shared.messaging;

import java.util.ArrayList;
import java.util.List;
import net.prismarin.api.messaging.NetworkMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalMessageBusTest {

    @Test
    void publishFillsEnvelopeAndDispatchesToSubscribers() {
        LocalMessageBus bus = new LocalMessageBus("lobby-1");
        List<NetworkMessage> received = new ArrayList<>();
        bus.subscribe("party", received::add);

        long before = System.currentTimeMillis();
        bus.publish("party", "{\"action\":\"invite\"}");

        assertEquals(1, received.size());
        NetworkMessage message = received.getFirst();
        assertNotNull(message.id());
        assertEquals("party", message.channel());
        assertEquals("lobby-1", message.sourceServer());
        assertTrue(message.timestamp() >= before);
        assertEquals("{\"action\":\"invite\"}", message.payloadJson());
        assertEquals("lobby-1", bus.serverId());
    }

    @Test
    void messagesOnOtherChannelsAreNotDelivered() {
        LocalMessageBus bus = new LocalMessageBus("lobby-1");
        List<NetworkMessage> received = new ArrayList<>();
        bus.subscribe("party", received::add);

        bus.publish("friends", "{}");

        assertTrue(received.isEmpty());
    }

    @Test
    void allSubscribersOfAChannelReceiveTheMessage() {
        LocalMessageBus bus = new LocalMessageBus("lobby-1");
        List<NetworkMessage> first = new ArrayList<>();
        List<NetworkMessage> second = new ArrayList<>();
        bus.subscribe("party", first::add);
        bus.subscribe("party", second::add);

        bus.publish("party", "{}");

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        assertEquals(first.getFirst().id(), second.getFirst().id());
    }

    @Test
    void closeRemovesAllSubscriptions() {
        LocalMessageBus bus = new LocalMessageBus("lobby-1");
        List<NetworkMessage> received = new ArrayList<>();
        bus.subscribe("party", received::add);
        bus.close();

        bus.publish("party", "{}");

        assertTrue(received.isEmpty());
    }
}
