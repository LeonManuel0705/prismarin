package net.prismarin.velocity;

import java.util.List;

public final class ProxyModules {

    public static final String MOTD = "motd";
    public static final String MAINTENANCE = "maintenance";
    public static final String HUB_COMMAND = "hub-command";
    public static final String SENDALL_COMMAND = "sendall-command";
    public static final String LOBBY_FALLBACK = "lobby-fallback";
    public static final String LOBBY_BALANCING = "lobby-balancing";
    public static final String STAFF_CHAT = "staff-chat";
    public static final String STAFF_LIST = "staff-list";
    public static final String FIND = "find";
    public static final String SEND = "send";
    public static final String GOTO = "goto";
    public static final String ALERT = "alert";
    public static final String PUNISHMENTS = "punishments";
    public static final String REPORTS = "reports";
    public static final String PLAYER_INFO = "player-info";
    public static final String AUDIT = "audit";
    public static final String NETEXEC = "netexec";
    public static final String SERVER_MAINTENANCE = "server-maintenance";
    public static final String NETWORK_ALERTS = "network-alerts";
    public static final String FRIENDS = "friends";
    public static final String PARTY = "party";
    public static final String PRIVATE_MESSAGES = "private-messages";

    public static final List<String> ALL = List.of(MOTD, MAINTENANCE, HUB_COMMAND, SENDALL_COMMAND, LOBBY_FALLBACK,
            LOBBY_BALANCING, STAFF_CHAT, STAFF_LIST, FIND, SEND, GOTO, ALERT, PUNISHMENTS, REPORTS, PLAYER_INFO, AUDIT,
            NETEXEC, SERVER_MAINTENANCE, NETWORK_ALERTS, FRIENDS, PARTY, PRIVATE_MESSAGES);

    private ProxyModules() {
        throw new AssertionError();
    }
}
