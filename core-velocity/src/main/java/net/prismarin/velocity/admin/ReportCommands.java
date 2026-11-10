package net.prismarin.velocity.admin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.shared.admin.Report;

public final class ReportCommands {

    private static final int LIST_LIMIT = 15;

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
                    return StaffCommands.filter(context.settings().reportReasons().stream()
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
                    chat(context, source, args[1]);
                    return;
                }
                list(context, flow, source);
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                String[] args = invocation.arguments();
                if (args.length <= 1) {
                    return StaffCommands.filter(List.of("claim", "close", "chat"), args.length == 0 ? "" : args[0]);
                }
                if (args.length == 3 && args[0].equalsIgnoreCase("close")) {
                    return StaffCommands.filter(List.of(Report.INVALID), args[2]);
                }
                return List.of();
            }
        };
    }

    private static void list(AdminContext context, ReportFlow flow, CommandSource source) {
        context.services().reports().open().thenAccept(open -> {
            context.send(source, "reports.header", Placeholder.unparsed("count", String.valueOf(open.size())));
            open.stream().limit(LIST_LIMIT).forEach(report -> {
                context.send(source, report.claimedBy().isEmpty() ? "reports.entry" : "reports.entry-claimed",
                        Placeholder.unparsed("id", report.id()),
                        Placeholder.unparsed("reporter", report.reporterName()),
                        Placeholder.unparsed("player", report.targetName()),
                        Placeholder.unparsed("server", report.server()),
                        Placeholder.unparsed("reason", report.reason()),
                        Placeholder.unparsed("count", String.valueOf(report.reporters())),
                        Placeholder.unparsed("staff", report.claimedBy()));
                source.sendMessage(flow.actions(source, report));
            });
        });
    }

    private static void chat(AdminContext context, CommandSource source, String id) {
        context.services().reports().find(id).thenAccept(found -> {
            if (found.isEmpty()) {
                context.send(source, "reports.unknown", Placeholder.unparsed("id", id));
                return;
            }
            Report report = found.get();
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
}
