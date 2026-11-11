package net.prismarin.api.level;

import java.util.Optional;

public final class LevelApi {

    private static volatile LevelService service;

    private LevelApi() {
        throw new AssertionError();
    }

    public static Optional<LevelService> service() {
        return Optional.ofNullable(service);
    }

    public static void register(LevelService levels) {
        service = levels;
    }

    public static void unregister() {
        service = null;
    }
}
