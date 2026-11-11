package net.prismarin.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.admin.MaintenanceState;
import net.prismarin.velocity.admin.AdminContext;
import net.prismarin.velocity.admin.ProxyMaintenance;
import net.prismarin.velocity.locale.ProxyMessages;

public final class MaintenanceCommand implements SimpleCommand {

    private static final String PERMISSION = "prismarin.admin.maintenance";

    private final ProxyMaintenance maintenance;
    private final ProxyMessages messages;

    public MaintenanceCommand(ProxyMaintenance maintenance, ProxyMessages messages) {
        this.maintenance = maintenance;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission(PERMISSION)) {
            messages.send(source, "general.no-permission");
            return;
        }
        String[] args = invocation.arguments();
        if (args.length == 3 && maintenance.serverSwitch() && (args[0].equalsIgnoreCase("server")
                || args[0].equalsIgnoreCase("group"))) {
            toggleTarget(source, args[0].toLowerCase(Locale.ROOT), args[1], args[2]);
            return;
        }
        if (args.length != 1) {
            messages.send(source, "maintenance.usage");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> enable(source);
            case "off" -> disable(source);
            case "status" -> status(source);
            default -> messages.send(source, "maintenance.usage");
        }
    }

    private void enable(CommandSource source) {
        if (maintenance.current().network()) {
            messages.send(source, "maintenance.already-on");
            return;
        }
        maintenance.setNetwork(true, AdminContext.actor(source));
        messages.send(source, "maintenance.enabled");
    }

    private void disable(CommandSource source) {
        if (!maintenance.current().network()) {
            messages.send(source, "maintenance.already-off");
            return;
        }
        maintenance.setNetwork(false, AdminContext.actor(source));
        messages.send(source, "maintenance.disabled");
    }

    private void status(CommandSource source) {
        MaintenanceState state = maintenance.current();
        messages.send(source, state.network() ? "maintenance.status-on" : "maintenance.status-off");
        if (maintenance.serverSwitch() && !state.targets().isEmpty()) {
            messages.send(source, "maintenance.targets",
                    Placeholder.unparsed("targets", String.join(", ", state.targets())));
        }
    }

    private void toggleTarget(CommandSource source, String kind, String name, String state) {
        boolean active = state.equalsIgnoreCase("on");
        if ((!active && !state.equalsIgnoreCase("off")) || !MaintenanceState.validName(name)) {
            messages.send(source, "maintenance.usage");
            return;
        }
        String actor = AdminContext.actor(source);
        String target = kind + ":" + name.toLowerCase(Locale.ROOT);
        (kind.equals("server") ? maintenance.setServer(name, active, actor) : maintenance.setGroup(name, active, actor))
                .thenRun(() -> messages.send(source, active ? "maintenance.target-on" : "maintenance.target-off",
                        Placeholder.unparsed("target", target)));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!invocation.source().hasPermission(PERMISSION)) {
            return List.of();
        }
        String[] args = invocation.arguments();
        if (args.length == 3) {
            return Stream.of("on", "off").filter(option -> option.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length > 1) {
            return List.of();
        }
        String current = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return Stream.of("on", "off", "status", "server", "group")
                .filter(option -> option.startsWith(current))
                .toList();
    }
}
