package net.prismarin.api.queue;

import java.util.Optional;

public final class QueueApi {

    public static final String BYPASS_PERMISSION = "prismarin.queue.bypass";

    private static volatile QueueService service;

    private QueueApi() {
        throw new AssertionError();
    }

    public static Optional<QueueService> service() {
        return Optional.ofNullable(service);
    }

    public static void register(QueueService queue) {
        service = queue;
    }

    public static void unregister() {
        service = null;
    }
}
