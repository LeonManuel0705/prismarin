package net.prismarin.core.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.Prismarin;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.core.ModuleState;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.storage.ProfileStorage;
import net.prismarin.shared.storage.StorageMigration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class PrismarinCommand implements TabExecutor {

    public static final String PERMISSION = "prismarin.admin.info";

    private final PrismarinCorePlugin plugin;
    private final Prismarin prismarin;
    private final CoreConfig config;
    private final ModuleState modules;
    private final LocaleService locale;
    private final ExecutorService executor;

    @Inject
    public PrismarinCommand(PrismarinCorePlugin plugin, Prismarin prismarin, CoreConfig config, ModuleState modules,
                            LocaleService locale, ExecutorService executor) {
        this.plugin = plugin;
        this.prismarin = prismarin;
        this.config = config;
        this.modules = modules;
        this.locale = locale;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Locale target = sender instanceof Player player ? player.locale() : Locale.forLanguageTag(config.defaultLocale());
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(locale.render("general.no-permission", target));
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("migrate")) {
            migrate(sender, target, args[1], args[2]);
            return true;
        }
        info(sender, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return Stream.of("info", "migrate").filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length <= 3 && args[0].equalsIgnoreCase("migrate")) {
            return Stream.of(CoreConfig.StorageMode.values()).map(Enum::name)
                    .filter(option -> option.startsWith(args[args.length - 1].toUpperCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    private void info(CommandSender sender, Locale target) {
        sender.sendMessage(locale.render("admin.info-header", target,
                Placeholder.unparsed("version", plugin.getPluginMeta().getVersion()),
                Placeholder.unparsed("server", config.serverId())));
        sender.sendMessage(locale.render("admin.info-storage", target,
                Placeholder.unparsed("storage", config.storageMode().name()),
                Placeholder.unparsed("bus", prismarin.messageBus().getClass().getSimpleName())));
        sender.sendMessage(locale.render("admin.info-systems", target,
                Placeholder.unparsed("systems", String.join(", ", prismarin.enabledSystems().stream().sorted().toList()))));
        sender.sendMessage(locale.render("admin.info-modules", target,
                Placeholder.unparsed("modules", String.join(", ", modules.enabledModules()))));
        modules.disabledReasons().forEach((module, reason) -> sender.sendMessage(locale.render("admin.info-off", target,
                Placeholder.unparsed("module", module), Placeholder.unparsed("reason", reason))));
        sender.sendMessage(locale.render("admin.info-players", target,
                Placeholder.unparsed("players", String.valueOf(plugin.getServer().getOnlinePlayers().size())),
                Placeholder.unparsed("servers", String.valueOf(prismarin.servers().servers().size()))));
    }

    private void migrate(CommandSender sender, Locale target, String fromName, String toName) {
        if (!(sender instanceof ConsoleCommandSender) && !(sender instanceof RemoteConsoleCommandSender)) {
            sender.sendMessage(locale.render("admin.migrate-console", target));
            return;
        }
        CoreConfig.StorageMode from;
        CoreConfig.StorageMode to;
        try {
            from = CoreConfig.StorageMode.valueOf(normalize(fromName));
            to = CoreConfig.StorageMode.valueOf(normalize(toName));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(locale.render("admin.migrate-usage", target, Placeholder.unparsed("modes",
                    Stream.of(CoreConfig.StorageMode.values()).map(Enum::name).collect(Collectors.joining(", ")))));
            return;
        }
        if (from == to) {
            sender.sendMessage(locale.render("admin.migrate-same", target));
            return;
        }
        sender.sendMessage(locale.render("admin.migrate-start", target, Placeholder.unparsed("from", from.name()),
                Placeholder.unparsed("to", to.name())));
        executor.execute(() -> {
            try (ProfileStorage source = ProfileStorage.create(config.withStorageMode(from),
                    plugin.getDataFolder().toPath());
                 ProfileStorage destination = ProfileStorage.create(config.withStorageMode(to),
                         plugin.getDataFolder().toPath())) {
                StorageMigration.Result result = StorageMigration.run(source, destination);
                sender.sendMessage(locale.render("admin.migrate-done", target,
                        Placeholder.unparsed("profiles", String.valueOf(result.profiles())),
                        Placeholder.unparsed("balances", String.valueOf(result.balances())),
                        Placeholder.unparsed("documents", String.valueOf(result.documents())),
                        Placeholder.unparsed("to", to.name())));
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Storage migration failed", e);
                sender.sendMessage(locale.render("admin.migrate-failed", target,
                        Placeholder.unparsed("reason", String.valueOf(e.getMessage()))));
            }
        });
    }

    private static String normalize(String mode) {
        String upper = mode.toUpperCase(Locale.ROOT);
        return upper.equals("MARIADB") ? "MYSQL" : upper;
    }
}
