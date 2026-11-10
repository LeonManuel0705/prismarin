package net.prismarin.velocity.admin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
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
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.PunishmentIssuedEvent;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.shared.admin.Report;
import net.prismarin.shared.admin.ReportService;

public final class ReportFlow {

    public static final String STAFF_REPORTS = "prismarin.staff.reports";

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

    public ReportFlow(AdminContext context, ChatHistory history) {
        this.context = context;
        this.history = history;
        this.nicks = new NickDirectory(context.services().documents());
        context.channel().handleReports(this::submit);
        context.services().events().subscribe(PunishmentIssuedEvent.class, event -> onPunished(event.punishment()));
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
            Optional<ReportReason> reason = context.settings().reason(args[1]);
            String details = String.join(" ", Arrays.copyOfRange(args, reason.isPresent() ? 2 : 1, args.length))
                    .trim();
            if (reason.isPresent()) {
                String label = reason.get().name(context.messages().fallback());
                file(player, target, details.isEmpty() ? label : label + ": " + details, reason.get().id());
                return;
            }
            if (context.settings().customReportReasons() || context.settings().reportReasons().isEmpty()) {
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
        Optional<ReportReason> reason = context.settings().reason(reasonId);
        if (reason.isEmpty() || coolingDown(reporter)) {
            return;
        }
        file(reporter, request.target().refresh(context.proxy()), reason.get().name(context.messages().fallback()),
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
        context.services().reports().claim(id, staff).thenAccept(claimed -> {
            if (claimed.isEmpty()) {
                context.send(source, "reports.unknown", Placeholder.unparsed("id", id));
                return;
            }
            context.services().audit().record(staff, "report-claim", claimed.get().targetName(), id);
            context.notifyStaff(STAFF_REPORTS, "reports.claimed",
                    Placeholder.unparsed("staff", staff),
                    Placeholder.unparsed("id", id),
                    Placeholder.unparsed("player", claimed.get().targetName()));
        });
    }

    void close(CommandSource source, String id, boolean invalid) {
        String staff = AdminContext.actor(source);
        String verdict = invalid ? Report.INVALID : Report.VALID;
        context.services().reports().close(id, staff, verdict).thenAccept(closed -> {
            if (closed.isEmpty()) {
                context.send(source, "reports.unknown", Placeholder.unparsed("id", id));
                return;
            }
            context.services().audit().record(staff, invalid ? "report-reject" : "report-close",
                    closed.get().targetName(), id);
            context.send(source, invalid ? "reports.rejected" : "reports.closed", Placeholder.unparsed("id", id));
            if (!invalid) {
                thank(closed.get().reporterIds(), closed.get().shownName());
            }
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
        if (report.claimedBy().isEmpty()) {
            buttons.add(button(viewer, "claim", "/reports claim " + report.id()));
        }
        buttons.add(button(viewer, "punish", "/punish " + report.targetName()));
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
        List<ReportReason> reasons = context.settings().reportReasons();
        if (reasons.isEmpty()) {
            context.send(player, "reports.usage");
            return;
        }
        Locale locale = context.messages().localeOf(player);
        if (context.messages().players().prismarinBackend(player)) {
            pending.put(player.getUniqueId(), new Pending(target, System.currentTimeMillis() + MENU_TIMEOUT_MILLIS));
            List<ModerationChannel.ReportMenuEntry> entries = new ArrayList<>();
            for (ReportReason reason : reasons) {
                entries.add(new ModerationChannel.ReportMenuEntry(reason.id(), reason.name(locale), reason.icon()));
            }
            context.channel().sendReportMenu(player, target.uuid(), target.shown(), entries);
            return;
        }
        context.send(player, "reports.choose", Placeholder.unparsed("player", target.shown()));
        Component hover = context.messages().render(player, "reports.choice-hover",
                Placeholder.unparsed("player", target.shown()));
        for (ReportReason reason : reasons) {
            player.sendMessage(context.messages().render(player, "reports.choice",
                            Placeholder.unparsed("reason", reason.name(locale)))
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
        ChatHistory.Evidence evidence = evidence(target, reasonId);
        List<String> chat = format(evidence, target.uuid());
        String server = target.online().map(StaffCommands::serverOf)
                .orElse(evidence.server().isEmpty() ? "offline" : evidence.server());
        String situation = target.online().map(ReportFlow::snapshot)
                .orElseGet(() -> context.messages().locale().raw("reports.offline-context", context.messages().fallback()))
                + (target.shown().equalsIgnoreCase(target.realName()) ? "" : " · Nick " + target.shown());
        ReportService.Draft draft = new ReportService.Draft(player.getUniqueId(), player.getUsername(),
                target.uuid(), target.realName(), reason, server, chat, situation, target.shown());
        String shown = target.shown();
        context.services().reports().submit(draft, context.settings().reportBehaviour().mergeWindowMillis())
                .thenAccept(submission -> {
                    Report report = submission.report();
                    switch (submission.outcome()) {
                        case DUPLICATE -> {
                            context.send(player, "reports.duplicate", Placeholder.unparsed("player", shown));
                            return;
                        }
                        case MERGED -> context.send(player, "reports.filed-merged", Placeholder.unparsed("player", shown));
                        case NEW -> context.send(player, "reports.filed", Placeholder.unparsed("player", shown));
                        default -> {
                        }
                    }
                    announce(report, submission.outcome(), player.getUsername(), reason, chat.size());
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
                Placeholder.unparsed("reason", reason),
                Placeholder.unparsed("count", String.valueOf(report.reporters())));
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

    private static String record(List<Punishment> punishments) {
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
