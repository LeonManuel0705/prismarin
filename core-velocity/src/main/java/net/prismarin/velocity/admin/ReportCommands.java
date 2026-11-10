package net.prismarin.velocity.admin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.shared.admin.Report;
import net.prismarin.velocity.social.Page;

public final class ReportCommands {

    private static final int LIST_LIMIT = 10;

    private ReportCommands() {
        throw new AssertionError();
    }

    public static SimpleCommand report(AdminContext context, ReportFlow flow) {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                if (!(invocation.source() instanceof Player player)) {
                    context.send(invocation.source(), "general.players-only");
                    return;
                }
                if (player.getPermissionValue("prismarin.report") == Tristate.FALSE) {
                    context.send(player, "general.no-permission");
                    return;
                }
                flow.command(player, invocation.arguments());
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                String[] args = invocation.arguments();
                if (args.length <= 1) {
                    return flow.suggestions(invocation.source(), args.length == 0 ? "" : args[0]);
                }
                if (args.length == 2) {
                    return StaffCommands.filter(flow.reasons().stream()
                            .map(ReportReason::id).toList(), args[1]);
                }
                return List.of();
            }
        };
    }

    public static SimpleCommand reports(AdminContext context, ReportFlow flow) {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                CommandSource source = invocation.source();
                if (!context.allowed(source, ReportFlow.STAFF_REPORTS)) {
                    return;
                }
                String[] args = invocation.arguments();
                if (args.length >= 2 && args[0].equalsIgnoreCase("close")) {
                    flow.close(source, args[1], args.length >= 3 && args[2].equalsIgnoreCase(Report.INVALID));
                    return;
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("claim")) {
                    flow.claim(source, args[1]);
                    return;
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("chat")) {
                    flow.showChat(source, args[1]);
                    return;
                }
                if (args.length == 3 && args[0].equalsIgnoreCase("action")) {
                    flow.runAction(source, args[2], args[1]);
                    return;
                }
                if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
                    list(context, flow, source, args.length >= 2 ? PunishmentCommands.page(args[1]) : 1);
                    return;
                }
                if (args.length == 0 && source instanceof Player player && flow.menu().available(player)) {
                    flow.menu().open(player);
                    return;
                }
                list(context, flow, source, 1);
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                String[] args = invocation.arguments();
                if (args.length <= 1) {
                    return StaffCommands.filter(List.of("list", "claim", "close", "chat"),
                            args.length == 0 ? "" : args[0]);
                }
                if (args.length == 3 && args[0].equalsIgnoreCase("close")) {
                    return StaffCommands.filter(List.of(Report.INVALID), args[2]);
                }
                if (args.length == 3 && args[0].equalsIgnoreCase("action")) {
                    return StaffCommands.filter(flow.actions(invocation.source()).stream()
                            .map(net.prismarin.api.moderation.ReportAction::id).toList(), args[2]);
                }
                return List.of();
            }
        };
    }

    private static void list(AdminContext context, ReportFlow flow, CommandSource source, int page) {
        context.services().reports().open().thenAccept(open -> {
            long now = System.currentTimeMillis();
            Page<Report> shown = ReportBoard.page(ReportBoard.arrange(open, ReportBoard.Filter.ALL,
                    ReportBoard.Sort.URGENT, AdminContext.actor(source), now, flow.claimMillis()), page - 1,
                    LIST_LIMIT);
            context.send(source, "reports.header", Placeholder.unparsed("count", String.valueOf(open.size())),
                    Placeholder.unparsed("page", String.valueOf(shown.page() + 1)),
                    Placeholder.unparsed("pages", String.valueOf(shown.pages())));
            for (Report report : shown.entries()) {
                boolean held = report.holder(now, flow.claimMillis()).isPresent();
                context.send(source, held ? "reports.entry-claimed" : "reports.entry",
                        Placeholder.unparsed("id", report.id()),
                        Placeholder.unparsed("reporter", report.reporterName()),
                        Placeholder.unparsed("player", report.targetName()),
                        Placeholder.unparsed("server", report.server()),
                        Placeholder.component("reason", flow.reasons(source, report)),
                        Placeholder.unparsed("count", String.valueOf(report.reporters())),
                        Placeholder.unparsed("age", flow.duration(source, now - report.createdAt())),
                        Placeholder.unparsed("staff", report.claimedBy()),
                        Placeholder.component("status", flow.status(source, report, now)));
                source.sendMessage(flow.actions(source, report));
            }
            if (shown.pages() > 1) {
                source.sendMessage(pager(context, source, shown.page() + 1, shown.pages()));
            }
        });
    }

    private static Component pager(AdminContext context, CommandSource source, int page, int pages) {
        Component line = Component.text(" ");
        if (page > 1) {
            line = line.append(context.messages().render(source, "reports.list-previous")
                    .clickEvent(ClickEvent.runCommand("/reports list " + (page - 1))));
            line = line.append(Component.text(" "));
        }
        if (page < pages) {
            line = line.append(context.messages().render(source, "reports.list-next")
                    .clickEvent(ClickEvent.runCommand("/reports list " + (page + 1))));
        }
        return line;
    }
}
