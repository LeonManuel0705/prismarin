package net.prismarin.velocity.admin;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.moderation.Report;
import net.prismarin.api.moderation.ReportAction;
import net.prismarin.api.moderation.ReportOutcome;
import net.prismarin.api.moderation.ReportSubmission;
import net.prismarin.api.moderation.ReportVerdict;
import net.prismarin.shared.admin.ReportService.Outcome;

public final class ProxyReportApi implements net.prismarin.api.moderation.ReportService {

    private final AdminContext context;
    private final ReportFlow flow;

    public ProxyReportApi(AdminContext context, ReportFlow flow) {
        this.context = context;
        this.flow = flow;
    }

    @Override
    public List<net.prismarin.api.moderation.ReportReason> reasons() {
        return flow.reasons().stream().map(ProxyReportApi::toApi).toList();
    }

    @Override
    public void registerReason(net.prismarin.api.moderation.ReportReason reason) {
        flow.registry().register(new ReportReason(reason.id(), reason.icon(), reason.names(),
                TextColors.parse(reason.color()).orElse(null)));
    }

    @Override
    public void unregisterReason(String id) {
        flow.registry().unregisterReason(id);
    }

    @Override
    public List<ReportAction> actions() {
        return flow.registry().actions();
    }

    @Override
    public void registerAction(ReportAction action) {
        flow.registry().register(action);
    }

    @Override
    public void unregisterAction(String id) {
        flow.registry().unregisterAction(id);
    }

    @Override
    public Duration claimTimeout() {
        return Duration.ofMillis(flow.claimMillis());
    }

    @Override
    public CompletableFuture<List<Report>> open() {
        return context.services().reports().open().thenApply(this::snapshots);
    }

    @Override
    public CompletableFuture<List<Report>> openAgainst(UUID target) {
        return context.services().reports().openAgainst(target).thenApply(this::snapshots);
    }

    @Override
    public CompletableFuture<Optional<Report>> find(String id) {
        return context.services().reports().find(id).thenApply(found -> found.map(flow::snapshot));
    }

    @Override
    public CompletableFuture<ReportSubmission> file(UUID reporter, String reporterName, UUID target, String reason) {
        return flow.fileFor(reporter, reporterName, target, reason).thenApply(submission ->
                new ReportSubmission(flow.snapshot(submission.report()), outcome(submission.outcome())));
    }

    @Override
    public CompletableFuture<Optional<Report>> claim(String id, String staff) {
        return flow.claimAs(staff, id, true).thenApply(claim -> claim.map(found -> flow.snapshot(found.report())));
    }

    @Override
    public CompletableFuture<Optional<Report>> release(String id) {
        return flow.release(id).thenApply(found -> found.map(flow::snapshot));
    }

    @Override
    public CompletableFuture<Optional<Report>> close(String id, String staff, ReportVerdict verdict) {
        return flow.closeAs(staff, id, verdict == null ? ReportVerdict.VALID : verdict)
                .thenApply(found -> found.map(flow::snapshot));
    }

    private List<Report> snapshots(List<net.prismarin.shared.admin.Report> reports) {
        return reports.stream().map(flow::snapshot).toList();
    }

    static ReportOutcome outcome(Outcome outcome) {
        return switch (outcome) {
            case NEW -> ReportOutcome.FILED;
            case MERGED -> ReportOutcome.MERGED;
            case DUPLICATE -> ReportOutcome.DUPLICATE;
        };
    }

    static net.prismarin.api.moderation.ReportReason toApi(ReportReason reason) {
        return new net.prismarin.api.moderation.ReportReason(reason.id(), reason.icon(), reason.names(),
                reason.color() == null ? "" : reason.color().asHexString());
    }
}
