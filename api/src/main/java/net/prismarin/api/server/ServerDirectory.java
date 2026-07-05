package net.prismarin.api.server;

import java.util.List;
import java.util.Optional;

/**
 * Read access to the set of known backend servers.
 */
public interface ServerDirectory {

    /**
     * Returns all known servers.
     *
     * @return an immutable snapshot of all registered servers
     */
    List<NetworkServer> servers();

    /**
     * Looks up a server by its technical name.
     *
     * @param name the unique technical name
     * @return the matching server, or empty if unknown
     */
    Optional<NetworkServer> byName(String name);
}
