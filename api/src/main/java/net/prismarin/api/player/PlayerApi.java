package net.prismarin.api.player;

import java.util.Optional;

public final class PlayerApi {

    private static volatile PlayerService service;

    private PlayerApi() {
        throw new AssertionError();
    }

    public static Optional<PlayerService> service() {
        return Optional.ofNullable(service);
    }

    public static PlayerService get() {
        PlayerService current = service;
        if (current == null) {
            throw new IllegalStateException("The PRISMARIN player API is not available. Declare PrismarinCore as a "
                    + "dependency of your plugin.");
        }
        return current;
    }

    public static void register(PlayerService players) {
        service = players;
    }

    public static void unregister() {
        service = null;
    }
}
