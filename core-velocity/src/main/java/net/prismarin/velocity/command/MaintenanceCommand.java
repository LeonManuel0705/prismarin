package net.prismarin.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.prismarin.velocity.config.ProxyConfigStore;
import net.prismarin.velocity.locale.ProxyMessages;

public final class MaintenanceCommand implements SimpleCommand {

    private static final String PERMISSION = "prismarin.admin.maintenance";

    private final ProxyConfigStore configStore;
    private final ProxyMessages messages;

    public MaintenanceCommand(ProxyConfigStore configStore, ProxyMessages messages) {
        this.configStore = configStore;
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
        if (configStore.maintenance()) {
            messages.send(source, "maintenance.already-on");
            return;
        }
        configStore.setMaintenance(true);
        messages.send(source, "maintenance.enabled");
    }

    private void disable(CommandSource source) {
        if (!configStore.maintenance()) {
            messages.send(source, "maintenance.already-off");
            return;
        }
        configStore.setMaintenance(false);
        messages.send(source, "maintenance.disabled");
    }

    private void status(CommandSource source) {
        messages.send(source, configStore.maintenance() ? "maintenance.status-on" : "maintenance.status-off");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!invocation.source().hasPermission(PERMISSION)) {
            return List.of();
        }
        String[] args = invocation.arguments();
        if (args.length > 1) {
            return List.of();
        }
        String current = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return Stream.of("on", "off", "status")
                .filter(option -> option.startsWith(current))
                .toList();
    }
}
