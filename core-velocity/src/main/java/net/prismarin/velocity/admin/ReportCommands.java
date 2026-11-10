package net.prismarin.velocity.admin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ReportCommands {

    private ReportCommands() {
        throw new AssertionError();
    }

    public static SimpleCommand report(AdminContext context) {
        Map<UUID, Long> lastReport = new ConcurrentHashMap<>();
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
                String[] args = invocation.arguments();
                if (args.length < 2) {
                    context.send(player, "reports.usage");
                    return;
                }
                long now = System.currentTimeMillis();
                long cooldown = context.settings().reportCooldownSeconds() * 1000L;
                Long last = lastReport.get(player.getUniqueId());
                if (last != null && now - last < cooldown && !player.hasPermission(StaffCommands.STAFF)) {
                    context.send(player, "reports.cooldown",
                            Placeholder.unparsed("seconds", String.valueOf((cooldown - (now - last)) / 1000L + 1L)));
                    return;
                }
                Player target = context.proxy().getPlayer(args[0]).orElse(null);
                if (target == null || target.equals(player)) {
                    context.send(player, "staff.not-online", Placeholder.unparsed("player", args[0]));
                    return;
                }
                lastReport.put(player.getUniqueId(), now);
                String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                String server = StaffCommands.serverOf(target);
                context.services().reports().file(player.getUniqueId(), player.getUsername(), target.getUniqueId(),
                        target.getUsername(), reason, server).thenAccept(report -> {
                            context.send(player, "reports.filed", Placeholder.unparsed("player", target.getUsername()));
                            context.notifyStaff("prismarin.staff.reports", "reports.notify",
                                    Placeholder.unparsed("id", report.id()),
                                    Placeholder.unparsed("reporter", player.getUsername()),
                                    Placeholder.unparsed("player", target.getUsername()),
                                    Placeholder.unparsed("server", server),
                                    Placeholder.unparsed("reason", reason));
                            DiscordWebhook.post(context.settings().discordWebhook(), "Report " + report.id() + ": "
                                    + player.getUsername() + " -> " + target.getUsername() + " (" + server + "): "
                                    + reason);
                        });
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                return invocation.arguments().length <= 1
                        ? StaffCommands.playerNames(context, invocation.source(), invocation.arguments())
                        : List.of();
            }
        };
    }

    public static SimpleCommand reports(AdminContext context) {
        return invocation -> {
            CommandSource source = invocation.source();
            if (!context.allowed(source, "prismarin.staff.reports")) {
                return;
            }
            String[] args = invocation.arguments();
            if (args.length == 2 && args[0].equalsIgnoreCase("close")) {
                context.services().reports().close(args[1], AdminContext.actor(source)).thenAccept(closed -> {
                    if (closed.isEmpty()) {
                        context.send(source, "reports.unknown", Placeholder.unparsed("id", args[1]));
                        return;
                    }
                    context.services().audit().record(AdminContext.actor(source), "report-close",
                            closed.get().targetName(), closed.get().id());
                    context.send(source, "reports.closed", Placeholder.unparsed("id", args[1]));
                });
                return;
            }
            context.services().reports().open().thenAccept(open -> {
                context.send(source, "reports.header", Placeholder.unparsed("count", String.valueOf(open.size())));
                open.stream().limit(15).forEach(report -> context.send(source, "reports.entry",
                        Placeholder.unparsed("id", report.id()),
                        Placeholder.unparsed("reporter", report.reporterName()),
                        Placeholder.unparsed("player", report.targetName()),
                        Placeholder.unparsed("server", report.server()),
                        Placeholder.unparsed("reason", report.reason())));
            });
        };
    }
}
