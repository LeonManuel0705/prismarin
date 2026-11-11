package net.prismarin.api.vanish;

import java.util.Optional;

public final class VanishApi {

    private static volatile VanishService service;

    private VanishApi() {
        throw new AssertionError();
    }

    public static Optional<VanishService> service() {
        return Optional.ofNullable(service);
    }

    public static void register(VanishService vanish) {
        service = vanish;
    }

    public static void unregister() {
        service = null;
    }
}
