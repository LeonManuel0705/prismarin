package net.prismarin.api.nick;

import java.util.Optional;

public final class NickApi {

    private static volatile NickService service;

    private NickApi() {
        throw new AssertionError();
    }

    public static Optional<NickService> service() {
        return Optional.ofNullable(service);
    }

    public static void register(NickService nicks) {
        service = nicks;
    }

    public static void unregister() {
        service = null;
    }
}
