package net.prismarin.shared.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.event.Subscription;

public final class SimpleEventBus implements EventBus {

    private static final Logger LOGGER = Logger.getLogger(SimpleEventBus.class.getName());

    private final List<Handler<?>> handlers = new CopyOnWriteArrayList<>();

    @Override
    public <E extends PrismarinEvent> Subscription subscribe(Class<E> type, Consumer<? super E> handler) {
        Handler<E> registered = new Handler<>(type, handler);
        handlers.add(registered);
        return () -> handlers.remove(registered);
    }

    @Override
    public void publish(PrismarinEvent event) {
        for (Handler<?> handler : handlers) {
            if (handler.type().isInstance(event)) {
                try {
                    handler.accept(event);
                } catch (RuntimeException e) {
                    LOGGER.log(Level.WARNING, "Event handler failed for " + event.getClass().getSimpleName(), e);
                }
            }
        }
    }

    private record Handler<E extends PrismarinEvent>(Class<E> type, Consumer<? super E> consumer) {
        void accept(PrismarinEvent event) {
            consumer.accept(type.cast(event));
        }
    }
}
