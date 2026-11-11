package net.prismarin.lobby.api;

import java.util.Objects;

public record LobbyInfo(String id, String displayName, Kind kind, String location, String permission,
                        int softMaxPlayers) {

    public enum Kind {
        WORLD,
        SERVER
    }

    public LobbyInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(location, "location");
        displayName = displayName == null ? id : displayName;
        permission = permission == null ? "" : permission.strip();
        softMaxPlayers = Math.max(0, softMaxPlayers);
    }

    public boolean premium() {
        return !permission.isEmpty();
    }
}
