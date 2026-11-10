package net.prismarin.api.server;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface NetworkConnector {

    CompletableFuture<Boolean> connect(UUID player, String server);

    Optional<NetworkServer> pick(String group);

    CompletableFuture<Optional<NetworkServer>> connectToGroup(UUID player, String group);
}
