package net.prismarin.api.moderation;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ReportService {

    List<ReportReason> reasons();

    void registerReason(ReportReason reason);

    void unregisterReason(String id);

    List<ReportAction> actions();

    void registerAction(ReportAction action);

    void unregisterAction(String id);

    Duration claimTimeout();

    CompletableFuture<List<Report>> open();

    CompletableFuture<List<Report>> openAgainst(UUID target);

    CompletableFuture<Optional<Report>> find(String id);

    CompletableFuture<ReportSubmission> file(UUID reporter, String reporterName, UUID target, String reason);

    CompletableFuture<Optional<Report>> claim(String id, String staff);

    CompletableFuture<Optional<Report>> release(String id);

    CompletableFuture<Optional<Report>> close(String id, String staff, ReportVerdict verdict);
}
