package net.prismarin.velocity.admin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.PunishmentIssuedEvent;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.api.moderation.ReportAction;
import net.prismarin.api.moderation.ReportClaimedEvent;
import net.prismarin.api.moderation.ReportClosedEvent;
import net.prismarin.api.moderation.ReportFiledEvent;
import net.prismarin.api.moderation.ReportMergedEvent;
import net.prismarin.api.moderation.ReportReleasedEvent;
import net.prismarin.api.moderation.ReportVerdict;
import net.prismarin.shared.admin.Report;
import net.prismarin.shared.admin.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ReportFlow {

    public static final String STAFF_REPORTS = "prismarin.staff.reports";

    private static final Logger LOGGER = LoggerFactory.getLogger("PrismarinCore");

    private static final long MENU_TIMEOUT_MILLIS = 120_000L;
    private static final long JOIN_DELAY_SECONDS = 3L;
    private static final String SEPARATOR = " ";

    private record Pending(Subject target, long expires) {
    }

    private final AdminContext context;
    private final ChatHistory history;
    private final NickDirectory nicks;
    private final Map<UUID, Long> lastReport = new ConcurrentHashMap<>();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final ReportsMenu menu;
    private final ReportRegistry registry;

    public ReportFlow(AdminContext context, ChatHistory history) {
        this.context = context;
        this.history = history;
        this.nicks = new NickDirectory(context.services().documents());
        this.registry = new ReportRegistry(context.settings().reportReasons(),
                context.settings().menuActions().stream().map(this::commandAction).toList());
        this.menu = new ReportsMenu(context, this);
        context.channel().handleReports(this::submit);
        context.services().events().subscribe(PunishmentIssuedEvent.class, event -> onPunished(event.punishment()));
    }

    ReportsMenu menu() {
        return menu;
    }

    public ReportRegistry registry() {
        return registry;
    }

    List<ReportReason> reasons() {
        return registry.reasons();
    }

    List<ReportAction> actions(CommandSource viewer) {
        return registry.actions().stream()
                .filter(action -> action.permission().isEmpty() || viewer.hasPermission(action.permission()))
                .toList();
    }

    private ReportAction commandAction(AdminSettings.MenuAction configured) {
        return new ReportAction(configured.id(), configured.icon(), configured.names(), configured.permission(),
                configured.close(), (staff, staffName, report) -> {
                    String command = ReportActions.fill(configured.command(), report, staffName);
                    if (configured.console() || staff.equals(ReportAction.CONSOLE)) {
                        context.proxy().getCommandManager().executeAsync(context.proxy().getConsoleCommandSource(),
                                command);
                        return;
                    }
                    context.proxy().getPlayer(staff).ifPresent(player -> {
                        String label = command.split(" ", 2)[0];
                        if (context.proxy().getCommandManager().hasCommand(label)) {
                            context.proxy().getCommandManager().executeAsync(player, command);
                        } else {
                            player.spoofChatInput("/" + command);
                        }
                    });
                });
    }

    void runAction(CommandSource source, String actionId, String reportId) {
        Optional<ReportAction> action = registry.action(actionId)
                .filter(found -> found.permission().isEmpty() || source.hasPermission(found.permission()));
        if (action.isEmpty()) {
            context.send(source, "reports.unknown-action", Placeholder.unparsed("action", actionId));
            return;
        }
        CompletableFuture<Optional<Report>> found = source instanceof Player
                ? touch(source, reportId, false).thenApply(claim -> claim.map(ReportService.Claim::report))
                : context.services().reports().find(reportId).thenApply(report -> report.filter(Report::open));
        found.thenAccept(report -> {
            if (report.isEmpty()) {
                context.send(source, "reports.unknown", Placeholder.unparsed("id", reportId));
                return;
            }
            UUID staff = source instanceof Player player ? player.getUniqueId() : ReportAction.CONSOLE;
            try {
                action.get().handler().run(staff, AdminContext.actor(source), snapshot(report.get()));
            } catch (RuntimeException error) {
                LOGGER.warn("Report action {} failed", actionId, error);
                context.send(source, "punishment.failed");
            }
        });
    }

    net.prismarin.api.moderation.Report snapshot(Report report) {
        List<net.prismarin.api.moderation.Report.Reporter> reporters = new ArrayList<>();
        reporters.add(new net.prismarin.api.moderation.Report.Reporter(report.reporter(), report.reporterName(),
                report.reason(), report.createdAt()));
        for (Report.Supporter supporter : report.supporters()) {
            if (supporter.uuid() != null) {
                reporters.add(new net.prismarin.api.moderation.Report.Reporter(supporter.uuid(), supporter.name(),
                        supporter.reason(), supporter.at()));
            }
        }
        return new net.prismarin.api.moderation.Report(report.id(), report.target(), report.targetName(),
                report.shownName(), report.server(), report.context(), reporters, report.chat(), report.createdAt(),
                report.holder(System.currentTimeMillis(), claimMillis()).orElse(""), report.claimedAt(),
                report.open(), report.closedBy(), report.closedAt(),
                report.open() ? null : ReportVerdict.byId(report.verdict()).orElse(ReportVerdict.VALID));
    }

    private void publish(PrismarinEvent event) {
        try {
            context.services().events().publish(event);
        } catch (RuntimeException error) {
            LOGGER.warn("A report event listener failed", error);
        }
    }

    public void releaseIdle() {
        context.services().reports().releaseIdle(claimMillis()).thenAccept(released -> released.forEach(release -> {
            if (!release.staff().isEmpty()) {
                publish(new ReportReleasedEvent(snapshot(release.report()), release.staff()));
            }
        })).exceptionally(error -> {
            LOGGER.warn("Releasing idle reports failed", error);
            return null;
        });
    }

    CompletableFuture<Optional<Report>> release(String id) {
        return context.services().reports().release(id).thenApply(found -> found.map(release -> {
            if (!release.staff().isEmpty()) {
                publish(new ReportReleasedEvent(snapshot(release.report()), release.staff()));
            }
            return release.report();
        }));
    }

    long claimMillis() {
        return context.settings().reportBehaviour().claimMillis();
    }

    void command(Player player, String[] args) {
        if (args.length == 0) {
            context.send(player, "reports.usage");
            return;
        }
        resolve(player, args[0]).thenAccept(found -> {
            if (found.isEmpty()) {
                context.send(player, "moderation.unknown-player", Placeholder.unparsed("player", args[0]));
                return;
            }
            Subject target = found.get();
            if (target.uuid().equals(player.getUniqueId())) {
                context.send(player, "reports.self");
                return;
            }
            if (coolingDown(player)) {
                return;
            }
            if (args.length == 1) {
                offer(player, target);
                return;
            }
            Optional<ReportReason> reason = registry.reason(args[1]);
            String details = String.join(" ", Arrays.copyOfRange(args, reason.isPresent() ? 2 : 1, args.length))
                    .trim();
            if (reason.isPresent()) {
                String label = reason.get().plain(context.messages().fallback());
                file(player, target, details.isEmpty() ? label : label + ": " + details, reason.get().id());
                return;
            }
            if (context.settings().customReportReasons() || registry.reasons().isEmpty()) {
                file(player, target, details, null);
                return;
            }
            offer(player, target);
        });
    }

    void submit(Player reporter, UUID targetId, String reasonId) {
        Pending request = pending.remove(reporter.getUniqueId());
        if (request == null || !request.target().uuid().equals(targetId)
                || request.expires() < System.currentTimeMillis()) {
            return;
        }
        Optional<ReportReason> reason = registry.reason(reasonId);
        if (reason.isEmpty() || coolingDown(reporter)) {
            return;
        }
        file(reporter, request.target().refresh(context.proxy()), reason.get().plain(context.messages().fallback()),
                reason.get().id());
    }

    private CompletableFuture<Optional<Subject>> resolve(Player reporter, String name) {
        Optional<Player> online = context.proxy().getPlayer(name);
        if (online.isPresent()) {
            Player player = online.get();
            return CompletableFuture.completedFuture(Optional.of(new Subject(player.getUniqueId(),
                    player.getUsername(), player.getUsername(), visible(reporter, player))));
        }
        Optional<UUID> nickOwner = nicks.ownerOf(name);
        if (nickOwner.isPresent()) {
            Optional<Player> owner = context.proxy().getPlayer(nickOwner.get());
            if (owner.isPresent()) {
                return CompletableFuture.completedFuture(Optional.of(new Subject(owner.get().getUniqueId(),
                        owner.get().getUsername(), name, visible(reporter, owner.get()))));
            }
            UUID uuid = nickOwner.get();
            return context.services().profiles().find(uuid).thenApply(profile -> Optional.of(new Subject(uuid,
                    profile.map(value -> value.getName()).orElse(name), name, Optional.empty())));
        }
        return context.resolve(name).thenApply(target -> target.map(value -> new Subject(value.uuid(),
                value.name(), value.name(), Optional.empty())));
    }

    private Optional<Player> visible(Player reporter, Player target) {
        return context.vanish().canSee(reporter, target) ? Optional.of(target) : Optional.empty();
    }

    record Subject(UUID uuid, String realName, String shown, Optional<Player> online) {

        Subject refresh(ProxyServer proxy) {
            return online.isPresent() ? new Subject(uuid, realName, shown, proxy.getPlayer(uuid)) : this;
        }
    }

    void claim(CommandSource source, String id) {
        String staff = AdminContext.actor(source);
        touch(source, id, true).thenAccept(claimed -> {
            if (claimed.isEmpty()) {
                context.send(source, "reports.unknown", Placeholder.unparsed("id", id));
                return;
            }
            Report report = claimed.get().report();
            if (claimed.get().outcome() == ReportService.ClaimOutcome.KEPT) {
                context.send(source, "reports.still-yours", Placeholder.unparsed("id", id),
                        Placeholder.unparsed("minutes", String.valueOf(context.settings().reportBehaviour()
                                .claimMinutes())));
                return;
            }
            context.services().audit().record(staff, "report-claim", report.targetName(), id);
            context.notifyStaff(STAFF_REPORTS, "reports.claimed",
                    Placeholder.unparsed("staff", staff),
                    Placeholder.unparsed("id", id),
                    Placeholder.unparsed("player", report.targetName()));
        });
    }

    CompletableFuture<Optional<ReportService.Claim>> touch(CommandSource source, String id, boolean force) {
        return claimAs(AdminContext.actor(source), id, force);
    }

    CompletableFuture<Optional<ReportService.Claim>> claimAs(String staff, String id, boolean force) {
        return context.services().reports().claim(id, staff, claimMillis(), force).thenApply(claim -> {
            claim.filter(found -> found.outcome() == ReportService.ClaimOutcome.CLAIMED)
                    .ifPresent(found -> publish(new ReportClaimedEvent(snapshot(found.report()), staff,
                            found.previous())));
            return claim;
        });
    }

    void showChat(CommandSource source, String id) {
        context.services().reports().find(id).thenAccept(found -> {
            if (found.isEmpty()) {
                context.send(source, "reports.unknown", Placeholder.unparsed("id", id));
                return;
            }
            Report report = found.get();
            if (report.open() && source instanceof Player) {
                touch(source, id, false);
            }
            if (report.chat().isEmpty()) {
                context.send(source, "reports.chat-none", Placeholder.unparsed("id", id));
                return;
            }
            context.send(source, "reports.chat-header", Placeholder.unparsed("id", report.id()),
                    Placeholder.unparsed("player", report.targetName()),
                    Placeholder.unparsed("server", report.server()));
            for (String line : report.chat()) {
                boolean reported = line.startsWith("> ");
                context.send(source, reported ? "reports.chat-line-reported" : "reports.chat-line",
                        Placeholder.unparsed("line", line.length() > 2 ? line.substring(2) : line));
            }
        });
    }

    Component reason(CommandSource viewer, String stored) {
        return ReportReason.describe(stored, registry.reasons(), context.messages().localeOf(viewer));
    }

    Component reasons(CommandSource viewer, Report report) {
        Component line = Component.empty();
        List<String> reasons = report.reasons();
        for (int index = 0; index < reasons.size(); index++) {
            if (index > 0) {
                line = line.append(Component.text(", ", NamedTextColor.DARK_GRAY));
            }
            line = line.append(reason(viewer, reasons.get(index)));
        }
        return line;
    }

    Component status(CommandSource viewer, Report report, long now) {
        Optional<String> holder = report.holder(now, claimMillis());
        if (holder.isEmpty()) {
            return context.messages().render(viewer, "reports.status.free");
        }
        if (holder.get().equalsIgnoreCase(AdminContext.actor(viewer))) {
            return context.messages().render(viewer, "reports.status.mine");
        }
        return context.messages().render(viewer, "reports.status.other", Placeholder.unparsed("staff", holder.get()));
    }

    String duration(CommandSource viewer, long millis) {
        Locale locale = context.messages().localeOf(viewer);
        return DurationText.format(Math.max(60_000L, millis),
                key -> context.messages().locale().raw("punishment.time." + key, locale));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pending.remove(uuid);
        menu.forget(uuid);
    }

    CompletableFuture<Void> close(CommandSource source, String id, boolean invalid) {
        return closeAs(AdminContext.actor(source), id, invalid ? ReportVerdict.INVALID : ReportVerdict.VALID)
                .thenAccept(closed -> context.send(source, closed.isEmpty() ? "reports.unknown"
                        : invalid ? "reports.rejected" : "reports.closed", Placeholder.unparsed("id", id)));
    }

    CompletableFuture<Optional<Report>> closeAs(String staff, String id, ReportVerdict verdict) {
        return context.services().reports().close(id, staff, verdict.id()).thenApply(closed -> {
            closed.ifPresent(report -> {
                context.services().audit().record(staff, verdict == ReportVerdict.INVALID ? "report-reject"
                        : "report-close", report.targetName(), id);
                if (verdict != ReportVerdict.INVALID) {
                    thank(report.reporterIds(), report.shownName());
                }
                publish(new ReportClosedEvent(snapshot(report), staff, verdict));
            });
            return closed;
        });
    }

    CompletableFuture<ReportService.Submission> fileFor(UUID reporter, String reporterName, UUID target,
                                                        String text) {
        Optional<Player> online = context.proxy().getPlayer(target);
        CompletableFuture<Optional<Subject>> subject = online.isPresent()
                ? CompletableFuture.completedFuture(Optional.of(new Subject(target, online.get().getUsername(),
                        nicks.shownName(online.get()), online)))
                : context.services().profiles().find(target).thenApply(profile -> profile.map(value ->
                        new Subject(target, value.getName(), nicks.shownNick(target).orElse(value.getName()),
                                Optional.empty())));
        return subject.thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(target + " never joined"));
            }
            String trimmed = text == null ? "" : text.trim();
            int space = trimmed.indexOf(' ');
            Optional<ReportReason> reason = registry.reason(space < 0 ? trimmed : trimmed.substring(0, space));
            if (reason.isEmpty()) {
                return store(reporter, reporterName, found.get(), trimmed, null);
            }
            String label = reason.get().plain(context.messages().fallback());
            String details = space < 0 ? "" : trimmed.substring(space + 1).trim();
            return store(reporter, reporterName, found.get(), details.isEmpty() ? label : label + ": " + details,
                    reason.get().id());
        });
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        CompletableFuture.runAsync(() -> {
            if (player.isActive()) {
                greet(player);
            }
        }, CompletableFuture.delayedExecutor(JOIN_DELAY_SECONDS, TimeUnit.SECONDS));
    }

    private void greet(Player player) {
        AdminSettings.ReportBehaviour behaviour = context.settings().reportBehaviour();
        if (behaviour.thankReporters()) {
            context.services().reports().takeFeedback(player.getUniqueId()).thenAccept(count -> {
                if (count > 0) {
                    context.send(player, "reports.thanks-later", Placeholder.unparsed("count", String.valueOf(count)));
                }
            });
        }
        if (behaviour.staffJoinReminder() && player.hasPermission(STAFF_REPORTS)) {
            context.services().reports().open().thenAccept(open -> {
                if (!open.isEmpty()) {
                    player.sendMessage(context.messages().render(player, "reports.reminder",
                                    Placeholder.unparsed("count", String.valueOf(open.size())))
                            .clickEvent(ClickEvent.runCommand("/reports"))
                            .hoverEvent(HoverEvent.showText(context.messages().render(player,
                                    "reports.reminder-hover"))));
                }
            });
        }
        if (behaviour.returnNotice()) {
            context.services().reports().openAgainst(player.getUniqueId()).thenAccept(open -> {
                if (!open.isEmpty()) {
                    context.notifyStaff(STAFF_REPORTS, "reports.returned",
                            Placeholder.unparsed("player", player.getUsername()),
                            Placeholder.unparsed("count", String.valueOf(open.size())));
                }
            });
        }
    }

    Component actions(CommandSource viewer, Report report) {
        List<Component> buttons = new ArrayList<>();
        buttons.add(button(viewer, "goto", "/goto " + report.targetName()));
        if (!report.chat().isEmpty()) {
            buttons.add(button(viewer, "chat", "/reports chat " + report.id()));
        }
        buttons.add(button(viewer, "info", "/pinfo " + report.targetName()));
        if (!report.heldBy(AdminContext.actor(viewer), System.currentTimeMillis(), claimMillis())) {
            buttons.add(button(viewer, "claim", "/reports claim " + report.id()));
        }
        buttons.add(button(viewer, "punish", "/punish " + report.targetName()));
        Locale locale = context.messages().localeOf(viewer);
        for (ReportAction action : actions(viewer)) {
            buttons.add(context.messages().render(viewer, "reports.action.custom",
                            Placeholder.component("name", ReportActions.label(action, locale)))
                    .clickEvent(ClickEvent.runCommand("/reports action " + report.id() + " " + action.id()))
                    .hoverEvent(HoverEvent.showText(context.messages().render(viewer, "reports.action.custom-hover",
                            Placeholder.component("name", ReportActions.label(action, locale))))));
        }
        buttons.add(button(viewer, "close", "/reports close " + report.id()));
        buttons.add(button(viewer, "reject", "/reports close " + report.id() + " invalid"));
        Component line = Component.text(" ");
        for (Component button : buttons) {
            line = line.append(button).append(Component.text(SEPARATOR));
        }
        return line;
    }

    private Component button(CommandSource viewer, String action, String command) {
        return context.messages().render(viewer, "reports.action." + action)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(context.messages().render(viewer, "reports.action." + action
                        + "-hover")));
    }

    List<String> suggestions(CommandSource source, String input) {
        boolean staff = source.hasPermission(STAFF_REPORTS);
        return StaffCommands.filter(context.proxy().getAllPlayers().stream()
                .filter(player -> context.vanish().canSee(source, player))
                .map(player -> staff ? player.getUsername() : nicks.shownName(player))
                .toList(), input);
    }

    private void offer(Player player, Subject target) {
        List<ReportReason> reasons = registry.reasons();
        if (reasons.isEmpty()) {
            context.send(player, "reports.usage");
            return;
        }
        Locale locale = context.messages().localeOf(player);
        if (context.messages().players().prismarinBackend(player)) {
            pending.put(player.getUniqueId(), new Pending(target, System.currentTimeMillis() + MENU_TIMEOUT_MILLIS));
            List<ModerationChannel.ReportMenuEntry> entries = new ArrayList<>();
            MiniMessage mini = MiniMessage.miniMessage();
            for (ReportReason reason : reasons) {
                entries.add(new ModerationChannel.ReportMenuEntry(reason.id(), mini.serialize(reason.display(locale)),
                        reason.icon()));
            }
            context.channel().sendReportMenu(player, target.uuid(), target.shown(), entries);
            return;
        }
        context.send(player, "reports.choose", Placeholder.unparsed("player", target.shown()));
        Component hover = context.messages().render(player, "reports.choice-hover",
                Placeholder.unparsed("player", target.shown()));
        for (ReportReason reason : reasons) {
            player.sendMessage(context.messages().render(player, "reports.choice",
                            Placeholder.component("reason", reason.display(locale)))
                    .clickEvent(ClickEvent.runCommand("/report " + target.shown() + " " + reason.id()))
                    .hoverEvent(HoverEvent.showText(hover)));
        }
    }

    private boolean coolingDown(Player player) {
        long cooldown = context.settings().reportCooldownSeconds() * 1000L;
        Long last = lastReport.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < cooldown && !player.hasPermission(StaffCommands.STAFF)) {
            context.send(player, "reports.cooldown",
                    Placeholder.unparsed("seconds", String.valueOf((cooldown - (now - last)) / 1000L + 1L)));
            return true;
        }
        return false;
    }

    private ChatHistory.Evidence evidence(Subject target, String reasonId) {
        AdminSettings.ChatEvidence settings = context.settings().chatEvidence();
        String server = target.online().map(StaffCommands::serverOf).orElse("");
        if (history == null || !settings.appliesTo(reasonId)) {
            return new ChatHistory.Evidence(server, List.of());
        }
        return target.online().isPresent()
                ? new ChatHistory.Evidence(server, history.recent(server, target.uuid(), settings.onlyReportedPlayer(),
                        settings.windowMillis(), settings.maxLines()))
                : history.lastSeen(target.uuid(), settings.onlyReportedPlayer(), settings.windowMillis(),
                        settings.maxLines());
    }

    private static List<String> format(ChatHistory.Evidence evidence, UUID target) {
        ZoneId zone = ZoneId.systemDefault();
        return evidence.lines().stream()
                .map(line -> (line.player().equals(target) ? "> " : "  ") + ChatHistory.format(line, zone))
                .toList();
    }

    private static String snapshot(Player target) {
        String brand = target.getClientBrand();
        return target.getProtocolVersion().getMostRecentSupportedVersion()
                + " · " + (brand == null || brand.isBlank() ? "?" : brand)
                + " · " + target.getPing() + " ms";
    }

    private void file(Player player, Subject target, String reason, String reasonId) {
        lastReport.put(player.getUniqueId(), System.currentTimeMillis());
        String shown = target.shown();
        store(player.getUniqueId(), player.getUsername(), target, reason, reasonId).thenAccept(submission -> {
            switch (submission.outcome()) {
                case DUPLICATE -> context.send(player, "reports.duplicate", Placeholder.unparsed("player", shown));
                case MERGED -> context.send(player, "reports.filed-merged", Placeholder.unparsed("player", shown));
                case NEW -> context.send(player, "reports.filed", Placeholder.unparsed("player", shown));
                default -> {
                }
            }
        });
    }

    private CompletableFuture<ReportService.Submission> store(UUID reporter, String reporterName, Subject target,
                                                              String reason, String reasonId) {
        ChatHistory.Evidence evidence = evidence(target, reasonId);
        List<String> chat = format(evidence, target.uuid());
        String server = target.online().map(StaffCommands::serverOf)
                .orElse(evidence.server().isEmpty() ? "offline" : evidence.server());
        String situation = target.online().map(ReportFlow::snapshot)
                .orElseGet(() -> context.messages().locale().raw("reports.offline-context", context.messages().fallback()))
                + (target.shown().equalsIgnoreCase(target.realName()) ? "" : " · Nick " + target.shown());
        ReportService.Draft draft = new ReportService.Draft(reporter, reporterName,
                target.uuid(), target.realName(), reason, server, chat, situation, target.shown());
        return context.services().reports().submit(draft, context.settings().reportBehaviour().mergeWindowMillis())
                .thenApply(submission -> {
                    Report report = submission.report();
                    if (submission.outcome() == ReportService.Outcome.DUPLICATE) {
                        return submission;
                    }
                    announce(report, submission.outcome(), reporterName, reason, chat.size());
                    net.prismarin.api.moderation.Report snapshot = snapshot(report);
                    publish(submission.outcome() == ReportService.Outcome.NEW ? new ReportFiledEvent(snapshot)
                            : new ReportMergedEvent(snapshot, snapshot.reporters().getLast()));
                    return submission;
                });
    }

    private void announce(Report report, ReportService.Outcome outcome, String reporter, String reason, int chatLines) {
        context.services().punishments().history(report.target())
                .exceptionally(error -> List.of())
                .thenCombine(context.services().reports().countAgainst(report.target()).exceptionally(error -> 0L),
                        (punishments, reports) -> {
                    String record = record(punishments);
                    boolean urgent = report.reporters() >= context.settings().reportBehaviour().priorityReporters();
                    for (Player staff : context.proxy().getAllPlayers()) {
                        if (staff.hasPermission(STAFF_REPORTS)) {
                            deliver(staff, report, outcome, reporter, reason, chatLines, record, reports, urgent);
                        }
                    }
                    deliver(context.proxy().getConsoleCommandSource(), report, outcome, reporter, reason, chatLines,
                            record, reports, urgent);
                    if (outcome == ReportService.Outcome.NEW || urgent) {
                        DiscordWebhook.post(context.settings().discordWebhook(), (urgent ? "URGENT " : "")
                                + "Report " + report.id() + " (" + report.reporters() + "x): " + reporter + " -> "
                                + report.targetName() + " (" + report.server() + "): " + reason + " | "
                                + report.context() + discordChat(report.chat()));
                    }
                    return null;
                });
    }

    private void deliver(CommandSource staff, Report report, ReportService.Outcome outcome, String reporter,
                         String reason, int chatLines, String record, long reports, boolean urgent) {
        if (urgent) {
            context.send(staff, "reports.urgent", Placeholder.unparsed("player", report.targetName()),
                    Placeholder.unparsed("count", String.valueOf(report.reporters())));
        }
        context.send(staff, outcome == ReportService.Outcome.MERGED ? "reports.notify-merged" : "reports.notify",
                Placeholder.unparsed("id", report.id()),
                Placeholder.unparsed("reporter", reporter),
                Placeholder.unparsed("player", report.targetName()),
                Placeholder.unparsed("server", report.server()),
                Placeholder.component("reason", reason(staff, reason)),
                Placeholder.unparsed("count", String.valueOf(report.reporters())),
                Placeholder.component("status", status(staff, report, System.currentTimeMillis())));
        context.send(staff, "reports.context",
                Placeholder.unparsed("client", report.context()),
                Placeholder.component("record", record.isEmpty()
                        ? context.messages().render(staff, "reports.record-clean")
                        : Component.text(record)),
                Placeholder.unparsed("reports", String.valueOf(reports)));
        if (chatLines > 0) {
            context.send(staff, "reports.chat-attached", Placeholder.unparsed("count", String.valueOf(chatLines)));
        }
        staff.sendMessage(actions(staff, report));
    }

    static String record(List<Punishment> punishments) {
        Map<PunishmentType, Long> counts = new EnumMap<>(PunishmentType.class);
        counts.putAll(punishments.stream().collect(Collectors.groupingBy(Punishment::type, Collectors.counting())));
        return counts.entrySet().stream()
                .map(entry -> entry.getValue() + "x " + entry.getKey().name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }

    private void onPunished(Punishment punishment) {
        if (!context.settings().reportBehaviour().closeOnPunishment()) {
            return;
        }
        context.services().reports().closeAllAgainst(punishment.target(), punishment.actor(), Report.PUNISHED)
                .thenAccept(closed -> {
                    if (closed.isEmpty()) {
                        return;
                    }
                    context.notifyStaff(STAFF_REPORTS, "reports.auto-closed",
                            Placeholder.unparsed("count", String.valueOf(closed.size())),
                            Placeholder.unparsed("player", punishment.targetName()),
                            Placeholder.unparsed("type", punishment.type().name().toLowerCase(Locale.ROOT)));
                    List<UUID> reporters = closed.stream().flatMap(report -> report.reporterIds().stream())
                            .distinct().toList();
                    thank(reporters, closed.getFirst().shownName());
                    closed.forEach(report -> publish(new ReportClosedEvent(snapshot(report), punishment.actor(),
                            ReportVerdict.PUNISHED)));
                });
    }

    private void thank(List<UUID> reporters, String targetName) {
        if (!context.settings().reportBehaviour().thankReporters()) {
            return;
        }
        List<UUID> offline = new ArrayList<>();
        for (UUID reporter : reporters) {
            Optional<Player> online = context.proxy().getPlayer(reporter);
            if (online.isPresent()) {
                context.send(online.get(), "reports.thanks", Placeholder.unparsed("player", targetName));
            } else {
                offline.add(reporter);
            }
        }
        if (!offline.isEmpty()) {
            context.services().reports().queueFeedback(offline);
        }
    }

    private static String discordChat(List<String> chat) {
        if (chat.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (int i = chat.size() - 1; i >= 0; i--) {
            String line = chat.get(i).replace("```", "'''") + "\n";
            if (text.length() + line.length() > 1_400) {
                break;
            }
            text.insert(0, line);
        }
        return "\n```\n" + text + "```";
    }
}
