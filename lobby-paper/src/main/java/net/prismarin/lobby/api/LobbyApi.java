package net.prismarin.lobby.api;

import java.util.Optional;

public final class LobbyApi {

    private static volatile LobbyService service;

    private LobbyApi() {
        throw new AssertionError();
    }

    public static Optional<LobbyService> service() {
        return Optional.ofNullable(service);
    }

    public static LobbyService get() {
        LobbyService current = service;
        if (current == null) {
            throw new IllegalStateException("PrismarinLobby is not enabled. Declare PrismarinLobby as a dependency "
                    + "of your plugin.");
        }
        return current;
    }

    public static void register(LobbyService lobby) {
        service = lobby;
    }

    public static void unregister() {
        service = null;
    }
}
