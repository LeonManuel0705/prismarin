package net.prismarin.api.server;

/**
 * Static description of one backend server in the network.
 *
 * @param name the unique technical name, e.g. {@code lobby-1}
 * @param displayName the display name in MiniMessage format
 * @param host the hostname or IP the server listens on
 * @param port the port the server listens on
 * @param category the coarse role of the server within the network
 */
public record NetworkServer(String name, String displayName, String host, int port, Category category) {

    /**
     * Coarse roles a backend server can fulfil.
     */
    public enum Category {

        /**
         * A hub players idle and navigate on.
         */
        LOBBY,

        /**
         * A server running an actual game mode.
         */
        GAME,

        /**
         * A creative or staff build server.
         */
        BUILD
    }
}
