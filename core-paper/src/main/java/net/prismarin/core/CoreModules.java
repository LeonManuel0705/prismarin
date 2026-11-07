package net.prismarin.core;

import java.util.List;

public final class CoreModules {

    public static final String CHAT = "chat";
    public static final String TABLIST = "tablist";
    public static final String SIDEBAR = "sidebar";
    public static final String JOIN_MESSAGES = "join-messages";
    public static final String ECONOMY_COMMANDS = "economy-commands";
    public static final String NETWORK_MENU = "network-menu";

    public static final List<String> ALL = List.of(CHAT, TABLIST, SIDEBAR, JOIN_MESSAGES, ECONOMY_COMMANDS,
            NETWORK_MENU);

    private CoreModules() {
        throw new AssertionError();
    }
}
