package net.prismarin.core;

import java.util.List;

public final class CoreModules {

    public static final String CHAT = "chat";
    public static final String TABLIST = "tablist";
    public static final String SIDEBAR = "sidebar";
    public static final String JOIN_MESSAGES = "join-messages";
    public static final String ECONOMY_COMMANDS = "economy-commands";
    public static final String NETWORK_MENU = "network-menu";
    public static final String PLAY_COMMAND = "play-command";
    public static final String VANISH = "vanish";
    public static final String MUTE_ENFORCEMENT = "mute-enforcement";
    public static final String NETEXEC = "netexec";
    public static final String VAULT = "vault";
    public static final String PLACEHOLDER_API = "placeholderapi";
    public static final String LEVEL_COMMANDS = "level-commands";
    public static final String RANK_COMMANDS = "rank-commands";
    public static final String NICK = "nick";
    public static final String STAFF_MODE = "staff-mode";
    public static final String ESSENTIALS = "essentials";
    public static final String CHAT_FILTER = "chat-filter";

    public static final List<String> ALL = List.of(CHAT, TABLIST, SIDEBAR, JOIN_MESSAGES, ECONOMY_COMMANDS,
            NETWORK_MENU, PLAY_COMMAND, VANISH, MUTE_ENFORCEMENT, NETEXEC, VAULT, PLACEHOLDER_API, LEVEL_COMMANDS,
            RANK_COMMANDS, NICK, ESSENTIALS, CHAT_FILTER);

    private CoreModules() {
        throw new AssertionError();
    }
}
