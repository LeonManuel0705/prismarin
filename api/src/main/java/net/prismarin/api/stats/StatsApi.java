package net.prismarin.api.stats;

import java.util.Optional;

public final class StatsApi {

    private static volatile StatsService service;

    private StatsApi() {
        throw new AssertionError();
    }

    public static Optional<StatsService> service() {
        return Optional.ofNullable(service);
    }

    public static void register(StatsService stats) {
        service = stats;
    }

    public static void unregister() {
        service = null;
    }
}
