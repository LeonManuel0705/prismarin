package net.prismarin.velocity.admin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.storage.ProfileStorage;
import net.prismarin.shared.storage.StorageMigration;

public final class ProxyInfoCommand implements SimpleCommand {

    private final AdminContext context;
    private final Path dataDirectory;
    private final String version;
    private final OperatorRegistry operators;

    public ProxyInfoCommand(AdminContext context, Path dataDirectory, String version, OperatorRegistry operators) {
        this.context = context;
        this.dataDirectory = dataDirectory;
        this.version = version;
        this.operators = operators;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!context.allowed(source, "prismarin.admin.info")) {
            return;
        }
        String[] args = invocation.arguments();
        if (args.length == 3 && args[0].equalsIgnoreCase("migrate")) {
            migrate(source, args[1], args[2]);
            return;
        }
        CoreConfig config = context.services().config();
        context.send(source, "admin.info-header", Placeholder.unparsed("version", version),
                Placeholder.unparsed("server", config.serverId()));
        context.send(source, "admin.info-storage", Placeholder.unparsed("storage", config.storageMode().name()),
                Placeholder.unparsed("bus", context.services().messageBus().getClass().getSimpleName()));
        context.send(source, "admin.info-systems", Placeholder.unparsed("systems",
                String.join(", ", context.services().enabledSystems().stream().sorted().toList())));
        context.send(source, "admin.info-players",
                Placeholder.unparsed("players", String.valueOf(context.proxy().getPlayerCount())),
                Placeholder.unparsed("servers", String.valueOf(context.proxy().getAllServers().size())),
                Placeholder.unparsed("vanished", String.valueOf(context.vanish().count())),
                Placeholder.unparsed("operators", operators == null ? "-" : String.valueOf(operators.count())));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            return StaffCommands.filter(List.of("info", "migrate"), args.length == 0 ? "" : args[0]);
        }
        if (args.length <= 3 && args[0].equalsIgnoreCase("migrate")) {
            return StaffCommands.filter(Stream.of(CoreConfig.StorageMode.values()).map(Enum::name).toList(),
                    args[args.length - 1]);
        }
        return List.of();
    }

    private void migrate(CommandSource source, String fromName, String toName) {
        if (!(source instanceof ConsoleCommandSource)) {
            context.send(source, "admin.migrate-console");
            return;
        }
        CoreConfig.StorageMode from;
        CoreConfig.StorageMode to;
        try {
            from = CoreConfig.StorageMode.valueOf(normalize(fromName));
            to = CoreConfig.StorageMode.valueOf(normalize(toName));
        } catch (IllegalArgumentException e) {
            context.send(source, "admin.migrate-usage", Placeholder.unparsed("modes",
                    Stream.of(CoreConfig.StorageMode.values()).map(Enum::name).collect(Collectors.joining(", "))));
            return;
        }
        if (from == to) {
            context.send(source, "admin.migrate-same");
            return;
        }
        context.send(source, "admin.migrate-start", Placeholder.unparsed("from", from.name()),
                Placeholder.unparsed("to", to.name()));
        CoreConfig config = context.services().config();
        Thread.ofVirtual().start(() -> {
            try (ProfileStorage sourceStorage = ProfileStorage.create(config.withStorageMode(from), dataDirectory);
                 ProfileStorage target = ProfileStorage.create(config.withStorageMode(to), dataDirectory)) {
                StorageMigration.Result result = StorageMigration.run(sourceStorage, target);
                context.send(source, "admin.migrate-done",
                        Placeholder.unparsed("profiles", String.valueOf(result.profiles())),
                        Placeholder.unparsed("balances", String.valueOf(result.balances())),
                        Placeholder.unparsed("documents", String.valueOf(result.documents())),
                        Placeholder.unparsed("to", to.name()));
            } catch (RuntimeException e) {
                context.send(source, "admin.migrate-failed",
                        Placeholder.unparsed("reason", String.valueOf(e.getMessage())));
            }
        });
    }

    private static String normalize(String mode) {
        String upper = mode.toUpperCase(Locale.ROOT);
        return upper.equals("MARIADB") ? "MYSQL" : upper;
    }
}
