package net.prismarin.shared.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import net.prismarin.api.admin.AuditEntry;
import net.prismarin.api.admin.AuditService;
import net.prismarin.api.admin.Broadcast;
import net.prismarin.api.admin.Delivery;
import net.prismarin.api.admin.MaintenanceService;
import net.prismarin.api.admin.MaintenanceState;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.admin.StaffAlert;
import net.prismarin.api.admin.StaffChat;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.PunishmentPreview;
import net.prismarin.api.moderation.PunishmentTemplate;
import net.prismarin.api.moderation.PunishmentTemplates;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.api.moderation.Report;
import net.prismarin.api.moderation.ReportAction;
import net.prismarin.api.moderation.ReportOutcome;
import net.prismarin.api.moderation.ReportReason;
import net.prismarin.api.moderation.ReportService;
import net.prismarin.api.moderation.ReportSubmission;
import net.prismarin.api.moderation.ReportVerdict;
import net.prismarin.shared.admin.AuditLog;
import net.prismarin.shared.admin.LocalAuditService;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemoteRoundTripTest {

    @TempDir
    Path directory;

    private final UUID suspect = UUID.randomUUID();
    private final UUID reporter = UUID.randomUUID();
    private final FakeMaintenance maintenance = new FakeMaintenance();
    private final FakeNotices notices = new FakeNotices();
    private final FakeReports reports = new FakeReports();
    private final FakeTemplates templates = new FakeTemplates();
    private AuditService proxyAudit;
    private AuditService backendAudit;
    private Loopback link;
    private boolean reportsOn = true;
    private boolean maintenanceOn = true;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(5_000L), ZoneOffset.UTC);
        proxyAudit = new LocalAuditService(new AuditLog(new JsonFileDocumentStore(directory.resolve("proxy")),
                Runnable::run, clock));
        backendAudit = new LocalAuditService(new AuditLog(new JsonFileDocumentStore(directory.resolve("backend")),
                Runnable::run, clock));
        RemoteDispatcher dispatcher = new RemoteDispatcher(proxyAudit,
                () -> maintenanceOn ? Optional.of(maintenance) : Optional.empty(), notices,
                () -> reportsOn ? Optional.of(reports) : Optional.empty(), () -> Optional.of(templates));
        link = new Loopback(dispatcher);
    }

    @Test
    void auditEntriesTravelToTheProxyStore() {
        RemoteAudit audit = new RemoteAudit(backendAudit, link, false);

        audit.record("AntiCheat", "anticheat.flag", "Steve", "Reach 4.2").join();

        List<AuditEntry> onProxy = proxyAudit.latest(10).join();
        assertEquals(1, onProxy.size());
        assertEquals("anticheat.flag", onProxy.getFirst().action());
        assertEquals(List.of(), backendAudit.latest(10).join());
        assertEquals(onProxy, audit.latest("Steve", 10).join());
        assertThrows(IllegalArgumentException.class, () -> audit.record("AntiCheat", "Bad Action", "", ""));
    }

    @Test
    void auditFallsBackToLocalStorageWithoutARoute() {
        RemoteAudit audit = new RemoteAudit(backendAudit, link, false);
        link.down = true;

        audit.record("AntiCheat", "anticheat.flag", "Steve", "").join();

        assertEquals(1, backendAudit.latest(10).join().size());
        assertEquals(1, audit.latest(10).join().size());
        assertEquals(List.of(), proxyAudit.latest(10).join());
    }

    @Test
    void sharedStorageSkipsTheProxy() {
        RemoteAudit audit = new RemoteAudit(backendAudit, link, true);

        audit.record("AntiCheat", "anticheat.flag", "Steve", "").join();
        audit.latest(5).join();

        assertEquals(0, link.calls.get());
        assertEquals(1, backendAudit.latest(10).join().size());
    }

    @Test
    void maintenanceIsReadAndSwitchedOnTheProxy() {
        RemoteMaintenance remote = new RemoteMaintenance(link);

        assertTrue(remote.setServer(" BW-1 ", true, "AntiCheat").join());
        assertFalse(remote.setServer("bw-1", true, "AntiCheat").join());
        assertTrue(remote.setGroup("bedwars", true, "Leon").join());
        assertTrue(remote.setNetwork(true, "Leon").join());

        MaintenanceState state = remote.state().join();
        assertTrue(state.network());
        assertEquals(Set.of("bw-1"), state.servers());
        assertEquals(Set.of("bedwars"), state.groups());
        assertEquals(List.of("AntiCheat", "AntiCheat", "Leon", "Leon"), maintenance.actors);
        assertThrows(IllegalArgumentException.class, () -> remote.setServer("a b", true, "Leon"));
    }

    @Test
    void switchedOffMaintenanceFailsWithTheProxyReason() {
        maintenanceOn = false;

        CompletionException error = assertThrows(CompletionException.class,
                () -> new RemoteMaintenance(link).state().join());

        assertInstanceOf(RemoteCallException.class, error.getCause());
        assertEquals("maintenance is off on the proxy", error.getCause().getMessage());
    }

    @Test
    void notificationsReachTheProxyWithTheirOrigin() {
        RemoteNotifications remote = new RemoteNotifications(link);
        StaffAlert alert = StaffAlert.of("AntiCheat", NoticeText.of("%player% flagged").and("de", "%player% auffällig")
                .with("player", "Steve")).permission("anticheat.alerts");
        Broadcast broadcast = Broadcast.group("bedwars", NoticeText.of("Event in 5 minutes"));

        assertEquals(Delivery.DELIVERED, remote.notifyStaff(alert).join());
        assertEquals(Delivery.DELIVERED, remote.staffChat("AntiCheat", "Steve flagged").join());
        assertEquals(Delivery.DELIVERED, remote.broadcast(broadcast).join());

        assertEquals(List.of(alert, new StaffChat("AntiCheat", "Steve flagged"), broadcast), notices.received);
        assertEquals(List.of("bw-1", "bw-1", "bw-1"), notices.origins);
    }

    @Test
    void notificationsWithoutRouteReportIt() {
        link.down = true;

        assertEquals(Delivery.NO_ROUTE, new RemoteNotifications(link).notifyStaff("AntiCheat",
                NoticeText.of("hello")).join());
        assertEquals(List.of(), notices.received);
    }

    @Test
    void reportsAreFiledListedAndClosedThroughTheProxy() {
        RemoteReports remote = new RemoteReports(link, new ModerationCatalog(link, Clock.systemUTC()));

        ReportSubmission submission = remote.file(reporter, "AntiCheat", suspect, "hacking Reach 4.2").join();
        String id = submission.report().id();

        assertEquals(ReportOutcome.FILED, submission.outcome());
        assertEquals("hacking Reach 4.2", submission.report().reason());
        assertEquals(Optional.of(submission.report()), remote.find(id).join());
        assertEquals(Optional.empty(), remote.find("missing").join());
        assertEquals(List.of(submission.report()), remote.open().join());
        assertEquals(List.of(submission.report()), remote.openAgainst(suspect).join());
        assertEquals(List.of(), remote.openAgainst(reporter).join());
        assertEquals("Leon", remote.claim(id, "Leon").join().orElseThrow().holder());
        assertEquals("", remote.release(id).join().orElseThrow().holder());
        Report closed = remote.close(id, "Leon", ReportVerdict.INVALID).join().orElseThrow();
        assertFalse(closed.open());
        assertEquals(Optional.of(ReportVerdict.INVALID), closed.closedAs());
    }

    @Test
    void reportsThatAreOffFailWithTheProxyReason() {
        reportsOn = false;
        RemoteReports remote = new RemoteReports(link, new ModerationCatalog(link, Clock.systemUTC()));

        CompletionException error = assertThrows(CompletionException.class,
                () -> remote.file(reporter, "AntiCheat", suspect, "hacking").join());

        assertEquals("reports are off on the proxy", error.getCause().getMessage());
        assertEquals(List.of(), remote.reasons());
    }

    @Test
    void reasonsMergeProxyAndOwnRegistrationsAndHealAfterAProxyRestart() {
        ModerationCatalog catalog = new ModerationCatalog(link, Clock.systemUTC());
        RemoteReports remote = new RemoteReports(link, catalog);
        ReportReason xray = new ReportReason("xray", "DIAMOND_ORE", Map.of("en", "X-Ray"), "#22D3EE");

        remote.registerReason(xray);

        assertEquals(List.of("hacking", "xray"), remote.reasons().stream().map(ReportReason::id).toList());
        assertEquals(Duration.ofMinutes(5), remote.claimTimeout());
        assertEquals(xray, reports.reasons.get("xray"));

        reports.reasons.remove("xray");
        catalog.refresh().join();

        assertEquals(xray, reports.reasons.get("xray"));
        remote.unregisterReason("xray");
        assertFalse(reports.reasons.containsKey("xray"));
        assertEquals(List.of("hacking"), remote.reasons().stream().map(ReportReason::id).toList());
    }

    @Test
    void reasonsTheProxyRefusesAreDropped() {
        RemoteReports remote = new RemoteReports(link, new ModerationCatalog(link, Clock.systemUTC()));

        remote.registerReason(new ReportReason("chat", "PAPER", Map.of("en", "Chat"), ""));

        assertFalse(remote.reasons().stream().anyMatch(reason -> reason.id().equals("chat")));
        assertThrows(UnsupportedOperationException.class, () -> remote.registerAction(new ReportAction("freeze",
                "ICE", Map.of("en", "Freeze"), "", true, (staff, name, report) -> { })));
        assertEquals(List.of(), remote.actions());
    }

    @Test
    void templatesKeepTheirDurationsAcrossTheWire() {
        ModerationCatalog catalog = new ModerationCatalog(link, Clock.systemUTC());
        RemoteTemplates remote = new RemoteTemplates(link, catalog);

        PunishmentTemplate fetched = remote.template("HACKING").orElseThrow();
        PunishmentPreview preview = remote.preview(suspect, "hacking").join();
        Punishment punishment = remote.punish(suspect, "hacking", "AntiCheat", "automatic").join();

        assertEquals(FakeTemplates.HACKING, fetched);
        assertEquals(Duration.ofDays(7), preview.step().duration());
        assertEquals(2, preview.levels());
        assertEquals(PunishmentType.BAN, punishment.type());
        assertEquals("AntiCheat", punishment.actor());
        assertEquals("automatic", punishment.note());
        assertEquals(suspect, punishment.target());

        PunishmentTemplate own = new PunishmentTemplate("fly", Map.of("en", "Fly"), Map.of(), "FEATHER", "",
                List.of(new PunishmentTemplate.Step(PunishmentType.KICK, null)), null);
        remote.register(own);
        assertTrue(templates.registered.containsKey("fly"));
        assertEquals(List.of("hacking", "fly"), remote.templates().stream().map(PunishmentTemplate::id).toList());
    }

    @Test
    void brokenBodiesAnswerWithReadableErrors() {
        RemoteDispatcher dispatcher = link.dispatcher;
        JsonObject alert = JsonParser.parseString("{\"alert\":{\"source\":\"\",\"permission\":\"\","
                + "\"text\":{\"templates\":{\"*\":\"hi\"},\"placeholders\":{}}}}").getAsJsonObject();
        JsonObject file = JsonParser.parseString("{\"target\":\"" + suspect + "\",\"reason\":\"x\"}")
                .getAsJsonObject();

        NetworkCalls.Answer badAlert = answer(dispatcher, NetworkCalls.Action.STAFF_ALERT, alert);
        NetworkCalls.Answer badFile = answer(dispatcher, NetworkCalls.Action.REPORT_FILE, file);
        NetworkCalls.Answer badKind = answer(dispatcher, NetworkCalls.Action.MAINTENANCE_SET, JsonParser.parseString(
                "{\"kind\":\"world\",\"active\":true}").getAsJsonObject());

        assertFalse(badAlert.ok());
        assertTrue(badAlert.error().contains("alert source"), badAlert.error());
        assertEquals("missing reporter", badFile.error());
        assertEquals("unknown maintenance kind", badKind.error());
    }

    @Test
    void longAuditListsAreCutToFitOnePluginMessage() {
        for (int index = 0; index < 60; index++) {
            proxyAudit.record("AntiCheat", "anticheat.flag", "Steve" + index, "x".repeat(1000)).join();
        }

        NetworkCalls.Answer answer = answer(link.dispatcher, NetworkCalls.Action.AUDIT_LATEST,
                JsonParser.parseString("{\"limit\":100}").getAsJsonObject());

        assertTrue(answer.ok(), answer.error());
        assertTrue(answer.body().get("truncated").getAsBoolean());
        int kept = answer.body().getAsJsonArray("entries").size();
        assertTrue(kept > 10 && kept < 60, String.valueOf(kept));
    }

    private static NetworkCalls.Answer answer(RemoteDispatcher dispatcher, NetworkCalls.Action action,
                                              JsonObject body) {
        JsonObject answer = dispatcher.dispatch(new NetworkCalls.Request(1, action, body), "bw-1").join();
        assertTrue(NetworkCalls.fits(answer));
        return NetworkCalls.parseAnswer(answer).orElseThrow();
    }

    static final class Loopback implements ProxyLink {

        final RemoteDispatcher dispatcher;
        final AtomicInteger calls = new AtomicInteger();
        boolean down;

        Loopback(RemoteDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public CompletableFuture<JsonObject> call(NetworkCalls.Action action, JsonObject body, UUID avoid) {
            calls.incrementAndGet();
            if (down) {
                return CompletableFuture.failedFuture(new NoRouteException());
            }
            JsonObject wire = JsonParser.parseString(NetworkCalls.request(calls.get(), action, body).toString())
                    .getAsJsonObject();
            NetworkCalls.Request request = NetworkCalls.parseRequest(wire).orElseThrow();
            return dispatcher.dispatch(request, "bw-1").thenApply(answer -> {
                NetworkCalls.Answer parsed = NetworkCalls.parseAnswer(JsonParser.parseString(answer.toString())
                        .getAsJsonObject()).orElseThrow();
                if (!parsed.ok()) {
                    throw new RemoteCallException(parsed.error());
                }
                return parsed.body();
            });
        }
    }

    static final class FakeMaintenance implements MaintenanceService {

        boolean network;
        final Set<String> servers = new TreeSet<>();
        final Set<String> groups = new TreeSet<>();
        final List<String> actors = new ArrayList<>();

        @Override
        public CompletableFuture<MaintenanceState> state() {
            return CompletableFuture.completedFuture(new MaintenanceState(network, servers, groups));
        }

        @Override
        public CompletableFuture<Boolean> setNetwork(boolean active, String actor) {
            actors.add(actor);
            boolean changed = network != active;
            network = active;
            return CompletableFuture.completedFuture(changed);
        }

        @Override
        public CompletableFuture<Boolean> setServer(String server, boolean active, String actor) {
            actors.add(actor);
            return CompletableFuture.completedFuture(active ? servers.add(server) : servers.remove(server));
        }

        @Override
        public CompletableFuture<Boolean> setGroup(String group, boolean active, String actor) {
            actors.add(actor);
            return CompletableFuture.completedFuture(active ? groups.add(group) : groups.remove(group));
        }
    }

    static final class FakeNotices implements RemoteDispatcher.NoticeSink {

        final List<Object> received = new ArrayList<>();
        final List<String> origins = new ArrayList<>();

        @Override
        public CompletableFuture<Delivery> staff(StaffAlert alert, String origin) {
            return take(alert, origin);
        }

        @Override
        public CompletableFuture<Delivery> chat(StaffChat message, String origin) {
            return take(message, origin);
        }

        @Override
        public CompletableFuture<Delivery> broadcast(Broadcast broadcast, String origin) {
            return take(broadcast, origin);
        }

        private CompletableFuture<Delivery> take(Object value, String origin) {
            received.add(value);
            origins.add(origin);
            return CompletableFuture.completedFuture(Delivery.DELIVERED);
        }
    }

    static final class FakeReports implements ReportService {

        final Map<String, ReportReason> reasons = new LinkedHashMap<>();
        final Map<String, Report> stored = new LinkedHashMap<>();

        FakeReports() {
            reasons.put("hacking", new ReportReason("hacking", "IRON_SWORD", Map.of("en", "Hacking"), "#FF5555"));
        }

        @Override
        public List<ReportReason> reasons() {
            return List.copyOf(reasons.values());
        }

        @Override
        public void registerReason(ReportReason reason) {
            if (reason.id().equals("chat")) {
                throw new IllegalArgumentException("chat is configured in admin.yml");
            }
            reasons.put(reason.id(), reason);
        }

        @Override
        public void unregisterReason(String id) {
            reasons.remove(id);
        }

        @Override
        public List<ReportAction> actions() {
            return List.of();
        }

        @Override
        public void registerAction(ReportAction action) {
        }

        @Override
        public void unregisterAction(String id) {
        }

        @Override
        public Duration claimTimeout() {
            return Duration.ofMinutes(5);
        }

        @Override
        public CompletableFuture<List<Report>> open() {
            return CompletableFuture.completedFuture(stored.values().stream().filter(Report::open).toList());
        }

        @Override
        public CompletableFuture<List<Report>> openAgainst(UUID target) {
            return CompletableFuture.completedFuture(stored.values().stream()
                    .filter(report -> report.open() && report.target().equals(target)).toList());
        }

        @Override
        public CompletableFuture<Optional<Report>> find(String id) {
            return CompletableFuture.completedFuture(Optional.ofNullable(stored.get(id)));
        }

        @Override
        public CompletableFuture<ReportSubmission> file(UUID reporter, String reporterName, UUID target,
                                                        String reason) {
            Report report = new Report(String.valueOf(stored.size() + 1), target, "Steve", "SilentFox", "bw-1", "",
                    List.of(new Report.Reporter(reporter, reporterName, reason, 1000L)), List.of("<Steve> ez"), 1000L,
                    "", 0L, true, "", 0L, null);
            stored.put(report.id(), report);
            return CompletableFuture.completedFuture(new ReportSubmission(report, ReportOutcome.FILED));
        }

        @Override
        public CompletableFuture<Optional<Report>> claim(String id, String staff) {
            return update(id, report -> copy(report, staff, report.open(), "", null));
        }

        @Override
        public CompletableFuture<Optional<Report>> release(String id) {
            return update(id, report -> copy(report, "", report.open(), "", null));
        }

        @Override
        public CompletableFuture<Optional<Report>> close(String id, String staff, ReportVerdict verdict) {
            return update(id, report -> copy(report, report.holder(), false, staff, verdict));
        }

        private CompletableFuture<Optional<Report>> update(String id, UnaryOperator<Report> change) {
            Report report = stored.computeIfPresent(id, (key, value) -> change.apply(value));
            return CompletableFuture.completedFuture(Optional.ofNullable(report));
        }

        private static Report copy(Report report, String holder, boolean open, String closedBy,
                                   ReportVerdict verdict) {
            return new Report(report.id(), report.target(), report.targetName(), report.shownName(), report.server(),
                    report.context(), report.reporters(), report.chat(), report.createdAt(), holder, 2000L, open,
                    closedBy, open ? 0L : 3000L, verdict);
        }
    }

    static final class FakeTemplates implements PunishmentTemplates {

        static final PunishmentTemplate HACKING = new PunishmentTemplate("hacking",
                Map.of("en", "Hacking", "de", "Hacking"), Map.of("en", "Cheats"), "IRON_SWORD", "prismarin.punish.hacking",
                List.of(new PunishmentTemplate.Step(PunishmentType.BAN, Duration.ofDays(7)),
                        new PunishmentTemplate.Step(PunishmentType.BAN, null)), Duration.ofDays(30));

        final Map<String, PunishmentTemplate> registered = new LinkedHashMap<>();

        @Override
        public List<PunishmentTemplate> templates() {
            List<PunishmentTemplate> all = new ArrayList<>(List.of(HACKING));
            all.addAll(registered.values());
            return all;
        }

        @Override
        public Optional<PunishmentTemplate> template(String id) {
            return templates().stream().filter(template -> template.id().equals(id)).findFirst();
        }

        @Override
        public void register(PunishmentTemplate template) {
            registered.put(template.id(), template);
        }

        @Override
        public void unregister(String id) {
            registered.remove(id);
        }

        @Override
        public CompletableFuture<PunishmentPreview> preview(UUID target, String templateId) {
            return CompletableFuture.completedFuture(new PunishmentPreview(target, templateId, 0, 1, 2,
                    HACKING.steps().getFirst()));
        }

        @Override
        public CompletableFuture<Punishment> punish(UUID target, String templateId, String actor, String note) {
            return CompletableFuture.completedFuture(new Punishment("p1", target, "Steve", PunishmentType.BAN,
                    "Hacking", actor, 1000L, 1000L + Duration.ofDays(7).toMillis(), true, "", 0L, templateId, note,
                    "", "", ""));
        }
    }
}
