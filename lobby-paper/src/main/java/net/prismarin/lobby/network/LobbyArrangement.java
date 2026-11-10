package net.prismarin.lobby.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.lobby.config.WorldLobby;
import net.prismarin.lobby.network.LobbyTarget.ServerTarget;
import net.prismarin.lobby.network.LobbyTarget.WorldTarget;

public final class LobbyArrangement {

    public static final String SOFT_MAX_PROPERTY = "soft-max-players";

    private LobbyArrangement() {
        throw new AssertionError();
    }

    public static List<LobbyTarget> arrange(List<WorldLobby> worldLobbies, List<NetworkServer> servers,
                                            String thisServer) {
        Optional<NetworkServer> self = servers.stream()
                .filter(server -> server.name().equalsIgnoreCase(thisServer))
                .findFirst();
        List<LobbyTarget> local = new ArrayList<>();
        if (self.isPresent() && worldLobbies.size() <= 1) {
            local.add(new ServerTarget(self.get()));
        } else {
            worldLobbies.forEach(lobby -> local.add(new WorldTarget(lobby)));
        }
        List<LobbyTarget> targets = new ArrayList<>();
        if (self.isEmpty()) {
            targets.addAll(local);
        }
        for (NetworkServer server : servers) {
            if (server.name().equalsIgnoreCase(thisServer)) {
                targets.addAll(local);
            } else {
                targets.add(new ServerTarget(server));
            }
        }
        return List.copyOf(targets);
    }

    public static int softMax(NetworkServer server) {
        return server.property(SOFT_MAX_PROPERTY).map(value -> {
            try {
                return Math.max(0, Integer.parseInt(value.strip()));
            } catch (NumberFormatException e) {
                return 0;
            }
        }).orElse(0);
    }
}
