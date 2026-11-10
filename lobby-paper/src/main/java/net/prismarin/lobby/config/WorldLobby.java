package net.prismarin.lobby.config;

import java.util.Objects;

public record WorldLobby(String id, boolean enabled, String world, String displayName, String icon,
                         int softMaxPlayers, String permission) {

    public WorldLobby {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(icon, "icon");
        permission = permission == null ? "" : permission.strip();
        softMaxPlayers = Math.max(0, softMaxPlayers);
    }

    public boolean premium() {
        return !permission.isEmpty();
    }
}
