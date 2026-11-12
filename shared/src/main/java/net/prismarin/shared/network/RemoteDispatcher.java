package net.prismarin.shared.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import net.prismarin.api.admin.AuditService;
import net.prismarin.api.admin.Broadcast;
import net.prismarin.api.admin.Delivery;
import net.prismarin.api.admin.MaintenanceService;
import net.prismarin.api.admin.StaffAlert;
import net.prismarin.api.admin.StaffChat;
import net.prismarin.api.moderation.AltService;
import net.prismarin.api.moderation.PunishmentTemplate;
import net.prismarin.api.moderation.PunishmentTemplates;
import net.prismarin.api.moderation.Report;
import net.prismarin.api.moderation.ReportReason;
import net.prismarin.api.moderation.ReportService;
import net.prismarin.api.moderation.ReportVerdict;
import net.prismarin.api.reward.Vote;
import net.prismarin.api.reward.VoteService;
import net.prismarin.api.queue.QueueService;
import net.prismarin.api.social.ClanService;
import net.prismarin.shared.joinme.JoinMeEndpoint;

public final class RemoteDispatcher {

    public static final int CLAIM_BATCH = 10;

    public interface NoticeSink {

        CompletableFuture<Delivery> staff(StaffAlert alert, String origin);

        CompletableFuture<Delivery> chat(StaffChat message, String origin);

        CompletableFuture<Delivery> broadcast(Broadcast broadcast, String origin);
    }

    private final AuditService audit;
    private final Supplier<Optional<MaintenanceService>> maintenance;
    private final NoticeSink notices;
    private final Supplier<Optional<ReportService>> reports;
    private final Supplier<Optional<PunishmentTemplates>> templates;
    private final Supplier<Optional<AltService>> alts;
    private final Supplier<Optional<ClanService>> clans;
    private final Supplier<Optional<VoteService>> votes;
    private final Supplier<Optional<QueueService>> queue;
    private final Supplier<Optional<JoinMeEndpoint>> joinMe;

    public RemoteDispatcher(AuditService audit, Supplier<Optional<MaintenanceService>> maintenance, NoticeSink notices,
                            Supplier<Optional<ReportService>> reports,
                            Supplier<Optional<PunishmentTemplates>> templates) {
        this(audit, maintenance, notices, reports, templates, Optional::empty, Optional::empty, Optional::empty,
                Optional::empty, Optional::empty);
    }

    public RemoteDispatcher(AuditService audit, Supplier<Optional<MaintenanceService>> maintenance, NoticeSink notices,
                            Supplier<Optional<ReportService>> reports,
                            Supplier<Optional<PunishmentTemplates>> templates, Supplier<Optional<AltService>> alts) {
        this(audit, maintenance, notices, reports, templates, alts, Optional::empty, Optional::empty, Optional::empty,
                Optional::empty);
    }

    public RemoteDispatcher(AuditService audit, Supplier<Optional<MaintenanceService>> maintenance, NoticeSink notices,
                            Supplier<Optional<ReportService>> reports,
                            Supplier<Optional<PunishmentTemplates>> templates, Supplier<Optional<AltService>> alts,
                            Supplier<Optional<ClanService>> clans, Supplier<Optional<VoteService>> votes,
                            Supplier<Optional<QueueService>> queue, Supplier<Optional<JoinMeEndpoint>> joinMe) {
        this.audit = Objects.requireNonNull(audit, "audit");
        this.maintenance = Objects.requireNonNull(maintenance, "maintenance");
        this.notices = Objects.requireNonNull(notices, "notices");
        this.reports = Objects.requireNonNull(reports, "reports");
        this.templates = Objects.requireNonNull(templates, "templates");
        this.alts = Objects.requireNonNull(alts, "alts");
        this.clans = Objects.requireNonNull(clans, "clans");
        this.votes = Objects.requireNonNull(votes, "votes");
        this.queue = Objects.requireNonNull(queue, "queue");
        this.joinMe = Objects.requireNonNull(joinMe, "joinMe");
    }

    public CompletableFuture<JsonObject> dispatch(NetworkCalls.Request request, String origin) {
        CompletableFuture<JsonObject> result;
        try {
            result = run(request.action(), request.body(), origin == null ? "" : origin);
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(NetworkCalls.failure(request.id(), message(e)));
        }
        return result.handle((body, error) -> error == null ? NetworkCalls.answer(request.id(), body)
                : NetworkCalls.failure(request.id(), message(error)));
    }

    private CompletableFuture<JsonObject> run(NetworkCalls.Action action, JsonObject body, String origin) {
        return switch (action) {
            case AUDIT_RECORD -> audit.record(ApiJson.string(body, "actor"), ApiJson.string(body, "action"),
                    ApiJson.stringOr(body, "target", ""), ApiJson.stringOr(body, "details", ""))
                    .thenApply(done -> new JsonObject());
            case AUDIT_LATEST -> audit.latest(blankToNull(ApiJson.stringOr(body, "target", "")),
                    ApiJson.number(body, "limit", 15)).thenApply(entries -> listed("entries", entries));
            case MAINTENANCE_STATE -> maintenance().state().thenApply(state -> single("state", ApiJson.tree(state)));
            case MAINTENANCE_SET -> setMaintenance(body).thenApply(changed -> flag("changed", changed));
            case STAFF_ALERT -> notices.staff(ApiJson.read(body.get("alert"), StaffAlert.class), origin)
                    .thenApply(RemoteDispatcher::delivery);
            case STAFF_CHAT -> notices.chat(ApiJson.read(body.get("chat"), StaffChat.class), origin)
                    .thenApply(RemoteDispatcher::delivery);
            case BROADCAST -> notices.broadcast(ApiJson.read(body.get("broadcast"), Broadcast.class), origin)
                    .thenApply(RemoteDispatcher::delivery);
            case MODERATION_CATALOG -> CompletableFuture.completedFuture(catalog());
            case REPORT_FILE -> reports().file(ApiJson.uuid(body, "reporter"), ApiJson.stringOr(body, "reporter-name", ""),
                    ApiJson.uuid(body, "target"), ApiJson.string(body, "reason"))
                    .thenApply(submission -> single("submission", ApiJson.tree(submission)));
            case REPORT_OPEN -> openReports(body).thenApply(found -> listed("reports", found));
            case REPORT_FIND -> reports().find(ApiJson.string(body, "id")).thenApply(RemoteDispatcher::report);
            case REPORT_CLAIM -> reports().claim(ApiJson.string(body, "id"), ApiJson.string(body, "staff"))
                    .thenApply(RemoteDispatcher::report);
            case REPORT_RELEASE -> reports().release(ApiJson.string(body, "id")).thenApply(RemoteDispatcher::report);
            case REPORT_CLOSE -> reports().close(ApiJson.string(body, "id"), ApiJson.string(body, "staff"),
                    ReportVerdict.byId(ApiJson.stringOr(body, "verdict", "")).orElse(ReportVerdict.VALID))
                    .thenApply(RemoteDispatcher::report);
            case REASON_REGISTER -> done(() -> reports().registerReason(
                    ApiJson.read(body.get("reason"), ReportReason.class)));
            case REASON_UNREGISTER -> done(() -> reports().unregisterReason(ApiJson.string(body, "id")));
            case TEMPLATE_REGISTER -> done(() -> templates().register(
                    ApiJson.read(body.get("template"), PunishmentTemplate.class)));
            case TEMPLATE_UNREGISTER -> done(() -> templates().unregister(ApiJson.string(body, "id")));
            case TEMPLATE_PREVIEW -> templates().preview(ApiJson.uuid(body, "target"), ApiJson.string(body, "template"))
                    .thenApply(preview -> single("preview", ApiJson.tree(preview)));
            case TEMPLATE_PUNISH -> templates().punish(ApiJson.uuid(body, "target"), ApiJson.string(body, "template"),
                    ApiJson.stringOr(body, "actor", ""), ApiJson.stringOr(body, "note", ""))
                    .thenApply(punishment -> single("punishment", ApiJson.tree(punishment)));
            case ALTS_FIND -> alts().alts(ApiJson.uuid(body, "player")).thenApply(found -> listed("alts", found));
            case CLAN_LOOKUP, CLAN_GET, CLAN_FIND, CLAN_LIST, CLAN_ACT, CLAN_MANAGE ->
                    ClanCalls.dispatch(clans(), action, body);
            case VOTE_SITES -> votes().sites().thenApply(sites -> listed("sites", sites));
            case VOTE_CAST -> castVote(body).thenApply(vote -> single("vote", ApiJson.tree(vote)));
            case VOTE_STATS -> votes().stats(ApiJson.uuid(body, "player"))
                    .thenApply(stats -> single("stats", ApiJson.tree(stats)));
            case VOTE_PENDING -> votes().pending(ApiJson.uuid(body, "player"))
                    .thenApply(found -> listed("votes", found));
            case VOTE_CLAIM -> votes().claim(ApiJson.uuid(body, "player"), ApiJson.stringOr(body, "name", ""),
                    Math.clamp(ApiJson.number(body, "limit", CLAIM_BATCH), 1, CLAIM_BATCH))
                    .thenApply(found -> listed("votes", found));
            case QUEUE_JOIN, QUEUE_LEAVE, QUEUE_POSITIONS, QUEUE_LIST, QUEUE_MANAGE ->
                    QueueCalls.dispatch(queue(), action, body);
            case JOINME_CHECK, JOINME_CREATE, JOINME_LIST, JOINME_FIND, JOINME_JOIN, JOINME_CANCEL, JOINME_PREVIEW ->
                    JoinMeCalls.dispatch(joinMe(), action, body);
        };
    }

    private CompletableFuture<Vote> castVote(JsonObject body) {
        String service = ApiJson.stringOr(body, "service", "");
        String player = ApiJson.stringOr(body, "player", "");
        if (!player.isEmpty()) {
            return votes().vote(ApiJson.uuid(body, "player"), service);
        }
        return votes().vote(ApiJson.string(body, "name"), service);
    }

    private CompletableFuture<Boolean> setMaintenance(JsonObject body) {
        boolean active = ApiJson.flag(body, "active");
        String actor = ApiJson.stringOr(body, "actor", "");
        return switch (ApiJson.string(body, "kind")) {
            case "network" -> maintenance().setNetwork(active, actor);
            case "server" -> maintenance().setServer(ApiJson.string(body, "name"), active, actor);
            case "group" -> maintenance().setGroup(ApiJson.string(body, "name"), active, actor);
            default -> throw new IllegalArgumentException("unknown maintenance kind");
        };
    }

    private CompletableFuture<List<Report>> openReports(JsonObject body) {
        String target = ApiJson.stringOr(body, "target", "");
        return target.isEmpty() ? reports().open() : reports().openAgainst(UUID.fromString(target));
    }

    private JsonObject catalog() {
        JsonObject body = new JsonObject();
        Optional<ReportService> reportService = reports.get();
        Optional<PunishmentTemplates> templateService = templates.get();
        body.addProperty("reports", reportService.isPresent());
        body.addProperty("templates", templateService.isPresent());
        body.add("reasons", ApiJson.array(reportService.map(ReportService::reasons).orElse(List.of())));
        body.addProperty("claim-millis", reportService.map(service -> service.claimTimeout().toMillis()).orElse(0L));
        body.add("template-list", ApiJson.array(templateService.map(PunishmentTemplates::templates)
                .orElse(List.of())));
        return body;
    }

    private MaintenanceService maintenance() {
        return maintenance.get().orElseThrow(() -> new IllegalStateException("maintenance is off on the proxy"));
    }

    private ReportService reports() {
        return reports.get().orElseThrow(() -> new IllegalStateException("reports are off on the proxy"));
    }

    private PunishmentTemplates templates() {
        return templates.get().orElseThrow(() -> new IllegalStateException("punishments are off on the proxy"));
    }

    private AltService alts() {
        return alts.get().orElseThrow(() -> new IllegalStateException("alt detection is off on the proxy"));
    }

    private ClanService clans() {
        return clans.get().orElseThrow(() -> new IllegalStateException("clans are off on the proxy"));
    }

    private VoteService votes() {
        return votes.get().orElseThrow(() -> new IllegalStateException("votes are off on the proxy"));
    }

    private QueueService queue() {
        return queue.get().orElseThrow(() -> new IllegalStateException("the queue is off on the proxy"));
    }

    private JoinMeEndpoint joinMe() {
        return joinMe.get().orElseThrow(() -> new IllegalStateException(JoinMeCalls.OFF));
    }

    private static CompletableFuture<JsonObject> done(Runnable action) {
        action.run();
        return CompletableFuture.completedFuture(new JsonObject());
    }

    private static JsonObject delivery(Delivery delivery) {
        JsonObject body = new JsonObject();
        body.addProperty("delivery", delivery.name());
        return body;
    }

    private static JsonObject report(Optional<Report> report) {
        return single("report", report.map(ApiJson::tree).orElse(JsonNull.INSTANCE));
    }

    private static JsonObject single(String key, JsonElement value) {
        JsonObject body = new JsonObject();
        body.add(key, value);
        return body;
    }

    private static JsonObject flag(String key, boolean value) {
        JsonObject body = new JsonObject();
        body.addProperty(key, value);
        return body;
    }

    private static JsonObject listed(String key, List<?> values) {
        JsonArray all = ApiJson.array(values);
        JsonArray kept = NetworkCalls.within(all, NetworkCalls.BODY_BUDGET);
        JsonObject body = new JsonObject();
        body.add(key, kept);
        body.addProperty("truncated", kept.size() < all.size());
        return body;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    static String message(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        for (Throwable inner = cause; inner != null; inner = inner.getCause()) {
            if ((inner instanceof IllegalArgumentException || inner instanceof IllegalStateException)
                    && inner.getMessage() != null) {
                return inner.getMessage();
            }
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
