package net.prismarin.shared.network;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.moderation.Report;
import net.prismarin.api.moderation.ReportAction;
import net.prismarin.api.moderation.ReportReason;
import net.prismarin.api.moderation.ReportService;
import net.prismarin.api.moderation.ReportSubmission;
import net.prismarin.api.moderation.ReportVerdict;

public final class RemoteReports implements ReportService {

    private final ProxyLink link;
    private final ModerationCatalog catalog;

    public RemoteReports(ProxyLink link, ModerationCatalog catalog) {
        this.link = link;
        this.catalog = catalog;
    }

    @Override
    public List<ReportReason> reasons() {
        return catalog.reasons();
    }

    @Override
    public void registerReason(ReportReason reason) {
        catalog.registerReason(Objects.requireNonNull(reason, "reason"));
    }

    @Override
    public void unregisterReason(String id) {
        catalog.unregisterReason(Objects.requireNonNull(id, "id"));
    }

    @Override
    public List<ReportAction> actions() {
        return List.of();
    }

    @Override
    public void registerAction(ReportAction action) {
        throw new UnsupportedOperationException("report actions run on the proxy, register them from a Velocity "
                + "plugin");
    }

    @Override
    public void unregisterAction(String id) {
        throw new UnsupportedOperationException("report actions run on the proxy, unregister them from a Velocity "
                + "plugin");
    }

    @Override
    public Duration claimTimeout() {
        return catalog.claimTimeout();
    }

    @Override
    public CompletableFuture<List<Report>> open() {
        return list(new JsonObject());
    }

    @Override
    public CompletableFuture<List<Report>> openAgainst(UUID target) {
        JsonObject body = new JsonObject();
        body.addProperty("target", Objects.requireNonNull(target, "target").toString());
        return list(body);
    }

    @Override
    public CompletableFuture<Optional<Report>> find(String id) {
        return single(NetworkCalls.Action.REPORT_FIND, withId(id));
    }

    @Override
    public CompletableFuture<ReportSubmission> file(UUID reporter, String reporterName, UUID target, String reason) {
        JsonObject body = new JsonObject();
        body.addProperty("reporter", Objects.requireNonNull(reporter, "reporter").toString());
        body.addProperty("reporter-name", reporterName == null ? "" : reporterName);
        body.addProperty("target", Objects.requireNonNull(target, "target").toString());
        body.addProperty("reason", Objects.requireNonNull(reason, "reason"));
        return link.call(NetworkCalls.Action.REPORT_FILE, body, target)
                .thenApply(answer -> ApiJson.read(answer.get("submission"), ReportSubmission.class));
    }

    @Override
    public CompletableFuture<Optional<Report>> claim(String id, String staff) {
        JsonObject body = withId(id);
        body.addProperty("staff", Objects.requireNonNull(staff, "staff"));
        return single(NetworkCalls.Action.REPORT_CLAIM, body);
    }

    @Override
    public CompletableFuture<Optional<Report>> release(String id) {
        return single(NetworkCalls.Action.REPORT_RELEASE, withId(id));
    }

    @Override
    public CompletableFuture<Optional<Report>> close(String id, String staff, ReportVerdict verdict) {
        JsonObject body = withId(id);
        body.addProperty("staff", Objects.requireNonNull(staff, "staff"));
        body.addProperty("verdict", (verdict == null ? ReportVerdict.VALID : verdict).id());
        return single(NetworkCalls.Action.REPORT_CLOSE, body);
    }

    private CompletableFuture<List<Report>> list(JsonObject body) {
        return link.call(NetworkCalls.Action.REPORT_OPEN, body)
                .thenApply(answer -> ApiJson.list(answer.get("reports"), Report.class));
    }

    private CompletableFuture<Optional<Report>> single(NetworkCalls.Action action, JsonObject body) {
        return link.call(action, body).thenApply(answer -> ApiJson.optional(answer.get("report"), Report.class));
    }

    private static JsonObject withId(String id) {
        JsonObject body = new JsonObject();
        body.addProperty("id", Objects.requireNonNull(id, "id"));
        return body;
    }
}
