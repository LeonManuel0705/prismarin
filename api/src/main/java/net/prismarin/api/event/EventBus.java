package net.prismarin.api.event;

import java.util.function.Consumer;

public interface EventBus {

    <E extends PrismarinEvent> Subscription subscribe(Class<E> type, Consumer<? super E> handler);

    void publish(PrismarinEvent event);
}
