package net.prismarin.lobby.api;

public enum LobbyMenu {
    NAVIGATOR("navigator"),
    PROFILE("profile"),
    LOBBY_SELECTOR("lobbies"),
    LANGUAGES("languages"),
    FRIENDS("friends"),
    PARTY("party"),
    CLAN("clan"),
    NICK_TOOL("nick");

    private final String id;

    LobbyMenu(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
