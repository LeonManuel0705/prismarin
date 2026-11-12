package net.prismarin.shared.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.admin.UpdateInfo;
import org.junit.jupiter.api.Test;

class UpdateCheckerTest {

    private static final String RELEASE = "{\"url\":\"https://api.github.com/x\",\"html_url\":"
            + "\"https://github.com/LeonManuel0705/prismarin/releases/tag/v1.0.5\",\"id\":1,"
            + "\"author\":{\"html_url\":\"https://github.com/someone\"},\"tag_name\":\"v1.0.5\"}";

    @Test
    void versionsCompareNumericallyAndTreatPreReleasesAsOlder() {
        assertTrue(Versions.newer("1.0.10", "1.0.9"));
        assertTrue(Versions.newer("v1.1", "1.0.9"));
        assertFalse(Versions.newer("1.0.4", "1.0.4"));
        assertTrue(Versions.newer("1.0.4", "1.0.4-SNAPSHOT"));
        assertFalse(Versions.newer("1.0.4-rc1", "1.0.4"));
        assertEquals(0, Versions.compare("1.0", "1.0.0"));
    }

    @Test
    void aNewerReleaseIsReportedOnceWithItsPage() {
        List<UpdateInfo> found = new ArrayList<>();
        UpdateChecker checker = new UpdateChecker("1.0.4", uri -> CompletableFuture.completedFuture(RELEASE), found::add);
        Optional<UpdateInfo> first = checker.check().join();
        checker.check().join();
        assertEquals("1.0.5", first.orElseThrow().latest());
        assertEquals("https://github.com/LeonManuel0705/prismarin/releases/tag/v1.0.5", first.orElseThrow().url());
        assertEquals(1, found.size());
        assertEquals(first, checker.available());
    }

    @Test
    void theSameOrAnOlderReleaseAndBrokenAnswersReportNothing() {
        List<UpdateInfo> found = new ArrayList<>();
        assertTrue(new UpdateChecker("1.0.5", uri -> CompletableFuture.completedFuture(RELEASE), found::add)
                .check().join().isEmpty());
        assertTrue(new UpdateChecker("1.0.4", uri -> CompletableFuture.completedFuture(""), found::add)
                .check().join().isEmpty());
        assertTrue(new UpdateChecker("1.0.4", uri -> CompletableFuture.failedFuture(new RuntimeException("offline")),
                found::add).check().join().isEmpty());
        assertTrue(found.isEmpty());
    }
}
