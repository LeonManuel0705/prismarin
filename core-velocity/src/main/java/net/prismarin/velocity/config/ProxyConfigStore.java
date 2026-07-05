package net.prismarin.velocity.config;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public final class ProxyConfigStore {

    private final Path file;
    private final Executor executor;
    private final Logger logger;
    private volatile ProxyConfig config;

    public ProxyConfigStore(Path file, Executor executor, Logger logger) {
        this.file = file;
        this.executor = executor;
        this.logger = logger;
        this.config = ProxyConfigCodec.load(file);
    }

    public ProxyConfig config() {
        return config;
    }

    public boolean maintenance() {
        return config.maintenance();
    }

    public CompletableFuture<Void> setMaintenance(boolean active) {
        ProxyConfig updated = config.withMaintenance(active);
        config = updated;
        return CompletableFuture.runAsync(() -> ProxyConfigCodec.save(file, updated), executor)
                .whenComplete((unused, throwable) -> {
                    if (throwable != null) {
                        logger.error("Could not persist maintenance state to {}", file, throwable);
                    }
                });
    }
}
