package net.prismarin.api.admin;

import java.util.concurrent.CompletableFuture;

public interface MaintenanceService {

    CompletableFuture<MaintenanceState> state();

    CompletableFuture<Boolean> setNetwork(boolean active, String actor);

    CompletableFuture<Boolean> setServer(String server, boolean active, String actor);

    CompletableFuture<Boolean> setGroup(String group, boolean active, String actor);
}
