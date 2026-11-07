package net.prismarin.core.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.permission.CorePermissions;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class EconomyCommand implements TabExecutor {

    private final PrismarinCorePlugin plugin;
    private final EconomyService economy;
    private final LocaleService locale;
    private final CoreConfig config;
    private final ExecutorService executor;

    @Inject
    public EconomyCommand(PrismarinCorePlugin plugin, EconomyService economy,
                          LocaleService locale, CoreConfig config, ExecutorService executor) {
        this.plugin = plugin;
        this.economy = economy;
        this.locale = locale;
        this.config = config;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(CorePermissions.ADMIN_ECONOMY)) {
            send(sender, "general.no-permission");
            return true;
        }
        EconomyCommandParser.Result result = EconomyCommandParser.parse(args, economy.currencies());
        if (result instanceof EconomyCommandParser.Invalid invalid) {
            send(sender, invalid.messageKey(), Placeholder.unparsed("currencies", currencyList()));
            return true;
        }
        EconomyCommandParser.Parsed parsed = (EconomyCommandParser.Parsed) result;
        Player online = Bukkit.getPlayerExact(parsed.targetName());
        if (online != null) {
            apply(sender, parsed, online.getUniqueId(), online.getName());
            return true;
        }
        executor.execute(() -> resolveOffline(sender, parsed));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(CorePermissions.ADMIN_ECONOMY)) {
            return List.of();
        }
        return switch (args.length) {
            case 1 -> filter(List.of("give", "take", "set"), args[0]);
            case 2 -> filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            case 3 -> filter(economy.currencies().stream().map(Currency::id).toList(), args[2]);
            case 4 -> filter(List.of("1", "10", "100", "1000"), args[3]);
            default -> List.of();
        };
    }

    private void resolveOffline(CommandSender sender, EconomyCommandParser.Parsed parsed) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(parsed.targetName());
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            send(sender, "general.player-not-found", Placeholder.unparsed("player", parsed.targetName()));
            return;
        }
        apply(sender, parsed, target.getUniqueId(), parsed.targetName());
    }

    private void apply(CommandSender sender, EconomyCommandParser.Parsed parsed, UUID targetId, String targetName) {
        switch (parsed.action()) {
            case GIVE -> economy.deposit(targetId, parsed.currency(), parsed.amount())
                    .whenComplete((result, error) -> handleTransfer(sender, parsed, targetId, targetName,
                            "economy.give-success", "economy.deposit-success", result, error));
            case TAKE -> economy.withdraw(targetId, parsed.currency(), parsed.amount())
                    .whenComplete((result, error) -> handleTransfer(sender, parsed, targetId, targetName,
                            "economy.take-success", "economy.withdraw-success", result, error));
            case SET -> economy.set(targetId, parsed.currency(), parsed.amount())
                    .whenComplete((result, error) -> handleSet(sender, parsed, targetId, targetName, result, error));
        }
    }

    private void handleTransfer(CommandSender sender, EconomyCommandParser.Parsed parsed, UUID targetId,
                                String targetName, String senderKey, String targetKey,
                                EconomyResult result, Throwable error) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Locale senderLocale = localeOf(sender);
            if (error != null || result == null) {
                sender.sendMessage(locale.render("economy.transaction-failed", senderLocale));
                return;
            }
            if (!result.success()) {
                if (result.failureReason() == EconomyResult.FailureReason.INSUFFICIENT_FUNDS) {
                    sender.sendMessage(locale.render("economy.take-insufficient", senderLocale,
                            Placeholder.unparsed("player", targetName),
                            currencyResolver(parsed.currency(), senderLocale),
                            Placeholder.unparsed("balance", String.valueOf(result.newBalance()))));
                } else {
                    sender.sendMessage(locale.render("general.player-not-found", senderLocale,
                            Placeholder.unparsed("player", targetName)));
                }
                return;
            }
            sender.sendMessage(locale.render(senderKey, senderLocale,
                    Placeholder.unparsed("player", targetName),
                    Placeholder.unparsed("amount", String.valueOf(parsed.amount())),
                    currencyResolver(parsed.currency(), senderLocale),
                    Placeholder.unparsed("balance", String.valueOf(result.newBalance()))));
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                target.sendMessage(locale.render(targetKey, target.locale(),
                        Placeholder.unparsed("amount", String.valueOf(parsed.amount())),
                        currencyResolver(parsed.currency(), target.locale()),
                        Placeholder.unparsed("balance", String.valueOf(result.newBalance()))));
            }
        });
    }

    private void handleSet(CommandSender sender, EconomyCommandParser.Parsed parsed, UUID targetId,
                           String targetName, EconomyResult result, Throwable error) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Locale senderLocale = localeOf(sender);
            if (error != null || result == null) {
                sender.sendMessage(locale.render("economy.transaction-failed", senderLocale));
                return;
            }
            if (!result.success()) {
                sender.sendMessage(locale.render("general.player-not-found", senderLocale,
                        Placeholder.unparsed("player", targetName)));
                return;
            }
            sender.sendMessage(locale.render("economy.set-success", senderLocale,
                    Placeholder.unparsed("player", targetName),
                    Placeholder.unparsed("amount", String.valueOf(parsed.amount())),
                    currencyResolver(parsed.currency(), senderLocale)));
        });
    }

    private void send(CommandSender sender, String key, TagResolver... resolvers) {
        Component message = locale.render(key, localeOf(sender), resolvers);
        if (Bukkit.isPrimaryThread()) {
            sender.sendMessage(message);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(message));
        }
    }

    private TagResolver currencyResolver(Currency currency, Locale target) {
        return Placeholder.component("currency", CurrencyNames.render(locale, currency, target));
    }

    private String currencyList() {
        return String.join(", ", economy.currencies().stream().map(Currency::id).toList());
    }

    private Locale localeOf(CommandSender sender) {
        return sender instanceof Player player
                ? player.locale()
                : Locale.forLanguageTag(config.defaultLocale());
    }

    private static List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }
}
