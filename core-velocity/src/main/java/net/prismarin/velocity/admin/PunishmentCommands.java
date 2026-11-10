package net.prismarin.velocity.admin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.velocity.admin.PunishmentArguments.Command;

public final class PunishmentCommands {

    private static final List<String> DURATIONS = List.of("30m", "1h", "6h", "1d", "7d", "30d");
    private static final Map<Command, List<PunishmentKind>> ENTRY = Map.of(
            Command.BAN, List.of(PunishmentKind.BAN, PunishmentKind.TEMPBAN),
            Command.TEMPBAN, List.of(PunishmentKind.TEMPBAN),
            Command.IPBAN, List.of(PunishmentKind.IPBAN),
            Command.MUTE, List.of(PunishmentKind.MUTE, PunishmentKind.TEMPMUTE),
            Command.TEMPMUTE, List.of(PunishmentKind.TEMPMUTE),
            Command.KICK, List.of(PunishmentKind.KICK),
            Command.WARN, List.of(PunishmentKind.WARN),
            Command.PUNISH, List.of());

    private PunishmentCommands() {
        throw new AssertionError();
    }

    public static Map<List<String>, SimpleCommand> all(AdminContext context, PunishmentFlow flow) {
        Map<List<String>, SimpleCommand> commands = new LinkedHashMap<>();
        commands.put(List.of("ban"), issue(context, flow, Command.BAN));
        commands.put(List.of("tempban"), issue(context, flow, Command.TEMPBAN));
        if (flow.settings().ipBans()) {
            commands.put(List.of("ipban", "banip"), issue(context, flow, Command.IPBAN));
        }
        commands.put(List.of("mute"), issue(context, flow, Command.MUTE));
        commands.put(List.of("tempmute"), issue(context, flow, Command.TEMPMUTE));
        commands.put(List.of("kick"), issue(context, flow, Command.KICK));
        commands.put(List.of("warn"), issue(context, flow, Command.WARN));
        commands.put(List.of("punish"), issue(context, flow, Command.PUNISH));
        commands.put(List.of("unban"), revoke(context, flow, PunishmentType.BAN));
        commands.put(List.of("unmute"), revoke(context, flow, PunishmentType.MUTE));
        commands.put(List.of("history"), history(context, flow));
        commands.put(List.of("check"), check(context, flow));
        return commands;
    }

    static boolean entry(CommandSource source, Command command) {
        if (source.hasPermission(PunishmentFlow.PUNISH) && command != Command.IPBAN) {
            return true;
        }
        return ENTRY.get(command).stream().anyMatch(kind -> PunishmentFlow.mayIssue(source, kind));
    }

    static SimpleCommand issue(AdminContext context, PunishmentFlow flow, Command command) {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                CommandSource source = invocation.source();
                if (!entry(source, command)) {
                    context.send(source, "general.no-permission");
                    return;
                }
                PunishmentArguments.Parsed parsed = PunishmentArguments.parse(command, invocation.arguments(),
                        id -> flow.settings().template(id).isPresent());
                switch (parsed) {
                    case PunishmentArguments.Usage usage ->
                            context.send(source, "punishment.usage." + command.label());
                    case PunishmentArguments.BadDuration bad -> context.send(source, "punishment.invalid-duration",
                            Placeholder.unparsed("duration", bad.text()));
                    case PunishmentArguments.UnknownTemplate unknown -> flow.unknownTemplate(source, unknown.id());
                    case PunishmentArguments.Menu menu -> flow.menu(source, menu.target());
                    case PunishmentArguments.Template template ->
                            flow.template(source, template.target(), template.id(), template.note());
                    case PunishmentArguments.Manual manual -> {
                        if (!PunishmentFlow.mayIssue(source, manual.kind())) {
                            context.send(source, "general.no-permission");
                            return;
                        }
                        flow.manual(source, manual);
                    }
                }
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                String[] args = invocation.arguments();
                if (!entry(invocation.source(), command)) {
                    return List.of();
                }
                if (args.length <= 1) {
                    return StaffCommands.playerNames(context, invocation.source(), args);
                }
                if (args.length != 2) {
                    return List.of();
                }
                List<String> options = new ArrayList<>();
                if (command != Command.IPBAN) {
                    flow.usableTemplates(invocation.source()).forEach(template -> options.add(template.id()));
                }
                if (command.type() != null && command.type().lasting()) {
                    options.addAll(DURATIONS);
                }
                return StaffCommands.filter(options, args[1]);
            }
        };
    }

    static SimpleCommand revoke(AdminContext context, PunishmentFlow flow, PunishmentType type) {
        String name = type == PunishmentType.BAN ? "unban" : "unmute";
        String fallback = type == PunishmentType.BAN ? "prismarin.staff.ban" : "prismarin.staff.mute";
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                CommandSource source = invocation.source();
                if (!source.hasPermission("prismarin.staff." + name) && !source.hasPermission(fallback)) {
                    context.send(source, "general.no-permission");
                    return;
                }
                String[] args = invocation.arguments();
                if (args.length == 0 || args[0].isBlank()) {
                    context.send(source, "punishment.usage." + name);
                    return;
                }
                flow.revoke(source, args[0], type, PunishmentArguments.rest(args, 1));
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                return invocation.arguments().length <= 1
                        ? StaffCommands.playerNames(context, invocation.source(), invocation.arguments())
                        : List.of();
            }
        };
    }

    static SimpleCommand history(AdminContext context, PunishmentFlow flow) {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                CommandSource source = invocation.source();
                if (!context.allowed(source, "prismarin.staff.history")) {
                    return;
                }
                String[] args = invocation.arguments();
                if (args.length == 0 || args.length > 2) {
                    context.send(source, "punishment.usage.history");
                    return;
                }
                flow.history(source, args[0], args.length == 2 ? page(args[1]) : 1);
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                return invocation.arguments().length <= 1
                        ? StaffCommands.playerNames(context, invocation.source(), invocation.arguments())
                        : List.of();
            }
        };
    }

    static SimpleCommand check(AdminContext context, PunishmentFlow flow) {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                CommandSource source = invocation.source();
                if (!source.hasPermission("prismarin.staff.check") && !source.hasPermission("prismarin.staff.history")) {
                    context.send(source, "general.no-permission");
                    return;
                }
                if (invocation.arguments().length != 1) {
                    context.send(source, "punishment.usage.check");
                    return;
                }
                flow.check(source, invocation.arguments()[0]);
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                return invocation.arguments().length <= 1
                        ? StaffCommands.playerNames(context, invocation.source(), invocation.arguments())
                        : List.of();
            }
        };
    }

    static int page(String text) {
        try {
            return Math.max(1, Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
