package net.prismarin.api.server;

import java.util.List;
import java.util.Optional;

public interface ServerDirectory {

    List<NetworkServer> servers();

    Optional<NetworkServer> byName(String name);

    default List<NetworkServer> byGroup(String group) {
        return servers().stream().filter(server -> server.inGroup(group)).toList();
    }
}
