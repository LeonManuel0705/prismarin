package net.prismarin.api.server;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface ServerStatusService {

    List<ServerStatus> statuses();

    Optional<ServerStatus> status(String name);

    CompletableFuture<List<ServerStatus>> refresh();
}
