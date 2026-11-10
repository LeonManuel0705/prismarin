package net.prismarin.api.player;

import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record ServerChangedEvent(UUID player, String previousServer, String currentServer) implements PrismarinEvent {

    public ServerChangedEvent {
        Objects.requireNonNull(player, "player");
        previousServer = previousServer == null ? "" : previousServer;
        currentServer = currentServer == null ? "" : currentServer;
    }

    public boolean joinedNetwork() {
        return previousServer.isEmpty();
    }
}
