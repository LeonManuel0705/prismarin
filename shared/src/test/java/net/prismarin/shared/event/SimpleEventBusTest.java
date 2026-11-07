package net.prismarin.shared.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.event.Subscription;
import org.junit.jupiter.api.Test;

class SimpleEventBusTest {

    private record Ping(int value) implements PrismarinEvent {
    }

    private record Pong() implements PrismarinEvent {
    }

    private final SimpleEventBus bus = new SimpleEventBus();

    @Test
    void deliversOnlyMatchingTypes() {
        List<Integer> received = new ArrayList<>();
        bus.subscribe(Ping.class, ping -> received.add(ping.value()));
        bus.publish(new Ping(1));
        bus.publish(new Pong());
        assertEquals(List.of(1), received);
    }

    @Test
    void supertypeSubscriptionsReceiveSubtypes() {
        List<PrismarinEvent> received = new ArrayList<>();
        bus.subscribe(PrismarinEvent.class, received::add);
        bus.publish(new Ping(1));
        bus.publish(new Pong());
        assertEquals(2, received.size());
    }

    @Test
    void unsubscribeStopsDelivery() {
        List<Integer> received = new ArrayList<>();
        Subscription subscription = bus.subscribe(Ping.class, ping -> received.add(ping.value()));
        subscription.unsubscribe();
        bus.publish(new Ping(1));
        assertEquals(List.of(), received);
    }

    @Test
    void failingHandlerDoesNotBlockOthers() {
        List<Integer> received = new ArrayList<>();
        bus.subscribe(Ping.class, ping -> {
            throw new IllegalStateException("boom");
        });
        bus.subscribe(Ping.class, ping -> received.add(ping.value()));
        bus.publish(new Ping(7));
        assertEquals(List.of(7), received);
    }
}
