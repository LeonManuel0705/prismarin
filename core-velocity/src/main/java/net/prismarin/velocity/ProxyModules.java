package net.prismarin.velocity;

import java.util.List;

public final class ProxyModules {

    public static final String MOTD = "motd";
    public static final String MAINTENANCE = "maintenance";
    public static final String HUB_COMMAND = "hub-command";
    public static final String SENDALL_COMMAND = "sendall-command";
    public static final String LOBBY_FALLBACK = "lobby-fallback";

    public static final List<String> ALL = List.of(MOTD, MAINTENANCE, HUB_COMMAND, SENDALL_COMMAND, LOBBY_FALLBACK);

    private ProxyModules() {
        throw new AssertionError();
    }
}
