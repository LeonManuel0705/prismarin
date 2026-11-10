package net.prismarin.lobby.network;

import java.util.Objects;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.lobby.config.WorldLobby;

public sealed interface LobbyTarget {

    String PERMISSION_PROPERTY = "permission";

    String id();

    String displayName();

    String permission();

    default boolean premium() {
        return !permission().isBlank();
    }

    record WorldTarget(WorldLobby lobby) implements LobbyTarget {

        public WorldTarget {
            Objects.requireNonNull(lobby, "lobby");
        }

        @Override
        public String id() {
            return lobby.id();
        }

        @Override
        public String displayName() {
            return lobby.displayName();
        }

        @Override
        public String permission() {
            return lobby.permission();
        }
    }

    record ServerTarget(NetworkServer server) implements LobbyTarget {

        public ServerTarget {
            Objects.requireNonNull(server, "server");
        }

        @Override
        public String id() {
            return server.name();
        }

        @Override
        public String displayName() {
            return server.displayName();
        }

        @Override
        public String permission() {
            return server.property(PERMISSION_PROPERTY).map(String::strip).orElse("");
        }
    }
}
