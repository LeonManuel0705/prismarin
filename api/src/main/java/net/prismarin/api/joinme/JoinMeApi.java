package net.prismarin.api.joinme;

import java.util.Optional;

public final class JoinMeApi {

    private static volatile JoinMeService service;

    private JoinMeApi() {
        throw new AssertionError();
    }

    public static Optional<JoinMeService> service() {
        return Optional.ofNullable(service);
    }

    public static void register(JoinMeService joinMe) {
        service = joinMe;
    }

    public static void unregister() {
        service = null;
    }
}
