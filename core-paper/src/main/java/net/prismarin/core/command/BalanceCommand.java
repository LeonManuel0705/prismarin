package net.prismarin.core.command;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.permission.CorePermissions;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BalanceCommand extends Command {

    public static final String OTHERS = "prismarin.command.balance.others";

    private final PrismarinCorePlugin plugin;
    private final ProfileService profiles;
    private final EconomyService economy;
    private final LocaleService locale;
    private final CoreConfig config;
    private final Currency currency;

    public BalanceCommand(String name, List<String> aliases, Currency currency, PrismarinCorePlugin plugin,
                          ProfileService profiles, EconomyService economy, LocaleService locale, CoreConfig config) {
        super(name, "Shows a balance.", "/" + name + " [player]", aliases);
        this.plugin = plugin;
        this.profiles = profiles;
        this.economy = economy;
        this.locale = locale;
        this.config = config;
        this.currency = currency;
        setPermission(CorePermissions.COMMAND_COINS);
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(CorePermissions.COMMAND_COINS)) {
            send(sender, "general.no-permission");
            return true;
        }
        if (args.length >= 1) {
            if (!sender.hasPermission(OTHERS)) {
                send(sender, "general.no-permission");
                return true;
            }
            lookup(args[0]).thenAccept(found -> {
                if (found.isEmpty()) {
                    send(sender, "general.player-not-found", Placeholder.unparsed("player", args[0]));
                    return;
                }
                show(sender, found.get(), true);
            });
            return true;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "general.players-only");
            return true;
        }
        Optional<PlayerProfile> profile = profiles.getCached(player.getUniqueId());
        if (profile.isEmpty()) {
            send(sender, "general.profile-not-loaded");
            return true;
        }
        show(sender, profile.get(), false);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(OTHERS)) {
            return List.of();
        }
        String input = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input)).sorted().toList();
    }

    private void show(CommandSender sender, PlayerProfile profile, boolean other) {
        Locale target = localeOf(sender);
        List<Currency> shown = currency == null ? economy.currencies() : List.of(currency);
        if (shown.size() > 1) {
            send(sender, other ? "economy.balance-all-other" : "economy.balance-all",
                    Placeholder.unparsed("player", profile.getName()));
            for (Currency entry : shown) {
                send(sender, "economy.balance-entry",
                        Placeholder.component("currency", CurrencyNames.render(locale, entry, target)),
                        Placeholder.unparsed("amount", String.valueOf(profile.balance(entry))));
            }
            return;
        }
        Currency single = shown.getFirst();
        send(sender, other ? "economy.balance-other" : "economy.balance",
                Placeholder.unparsed("player", profile.getName()),
                Placeholder.unparsed("amount", String.valueOf(profile.balance(single))),
                Placeholder.component("currency", CurrencyNames.render(locale, single, target)));
    }

    private CompletableFuture<Optional<PlayerProfile>> lookup(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null && profiles.getCached(online.getUniqueId()).isPresent()) {
            return CompletableFuture.completedFuture(profiles.getCached(online.getUniqueId()));
        }
        CompletableFuture<Optional<UUID>> uuid = online != null
                ? CompletableFuture.completedFuture(Optional.of(online.getUniqueId()))
                : profiles.lookup(name);
        return uuid.thenCompose(found -> found.isEmpty()
                ? CompletableFuture.completedFuture(Optional.<PlayerProfile>empty())
                : profiles.find(found.get()));
    }

    private Locale localeOf(CommandSender sender) {
        return sender instanceof Player player ? PlayerLocales.of(player, profiles)
                : Locale.forLanguageTag(config.defaultLocale());
    }

    private void send(CommandSender sender, String key, TagResolver... resolvers) {
        Component message = locale.render(key, localeOf(sender), resolvers);
        if (Bukkit.isPrimaryThread()) {
            sender.sendMessage(message);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(message));
        }
    }
}
