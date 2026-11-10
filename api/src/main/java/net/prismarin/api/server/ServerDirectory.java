package net.prismarin.api.server;

import java.util.List;
import java.util.Optional;

public interface ServerDirectory {

    List<NetworkServer> servers();

    Optional<NetworkServer> byName(String name);

    default List<ServerGroup> groups() {
        return List.of();
    }

    default Optional<ServerGroup> group(String name) {
        return groups().stream().filter(group -> group.name().equalsIgnoreCase(name)).findFirst();
    }

    default List<NetworkServer> byGroup(String group) {
        return servers().stream().filter(server -> server.inGroup(group)).toList();
    }
}
