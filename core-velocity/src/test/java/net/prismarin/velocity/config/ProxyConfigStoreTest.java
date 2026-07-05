package net.prismarin.velocity.config;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyConfigStoreTest {

    @TempDir
    Path directory;

    private final Logger logger = LoggerFactory.getLogger(ProxyConfigStoreTest.class);

    @Test
    void persistsMaintenanceStateAcrossInstances() {
        Path file = directory.resolve("proxy.yml");
        ProxyConfigStore store = new ProxyConfigStore(file, Runnable::run, logger);
        assertFalse(store.maintenance());
        store.setMaintenance(true).join();
        assertTrue(store.maintenance());
        ProxyConfigStore reloaded = new ProxyConfigStore(file, Runnable::run, logger);
        assertTrue(reloaded.maintenance());
    }

    @Test
    void togglesMaintenanceBackOff() {
        Path file = directory.resolve("proxy.yml");
        ProxyConfigStore store = new ProxyConfigStore(file, Runnable::run, logger);
        store.setMaintenance(true).join();
        store.setMaintenance(false).join();
        assertFalse(store.maintenance());
        assertFalse(new ProxyConfigStore(file, Runnable::run, logger).maintenance());
    }
}
