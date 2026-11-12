package net.prismarin.shared.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.prismarin.api.admin.ReloadResult;
import net.prismarin.api.event.Subscription;
import org.junit.jupiter.api.Test;

class ReloadRegistryTest {

    @Test
    void everyPartRunsAndAFailureDoesNotStopTheOthers() {
        ReloadRegistry registry = new ReloadRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.register("languages", calls::incrementAndGet);
        registry.register("broken", () -> {
            throw new IllegalStateException("file missing");
        });
        registry.register("rank display", calls::incrementAndGet);
        ReloadResult result = registry.reloadAll();
        assertEquals(2, calls.get());
        assertEquals(List.of("languages", "rank display"), result.reloaded());
        assertEquals("file missing", result.failed().get("broken"));
        assertFalse(result.complete());
    }

    @Test
    void anUnsubscribedPartIsNoLongerReloaded() {
        ReloadRegistry registry = new ReloadRegistry();
        Subscription subscription = registry.register("plugin", () -> { });
        subscription.unsubscribe();
        assertTrue(registry.parts().isEmpty());
        assertTrue(registry.reloadAll().complete());
    }
}
