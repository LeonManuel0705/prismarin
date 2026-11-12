package net.prismarin.api.admin;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface UpdateService {

    String currentVersion();

    Optional<UpdateInfo> available();

    CompletableFuture<Optional<UpdateInfo>> check();
}
