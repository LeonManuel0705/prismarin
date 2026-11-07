package net.prismarin.core.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.permission.CorePermissions;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class CoinsCommand implements TabExecutor {

    private final ProfileService profiles;
    private final EconomyService economy;
    private final LocaleService locale;
    private final CoreConfig config;

    @Inject
    public CoinsCommand(ProfileService profiles, EconomyService economy, LocaleService locale, CoreConfig config) {
        this.profiles = profiles;
        this.economy = economy;
        this.locale = locale;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(locale.render("general.players-only",
                    Locale.forLanguageTag(config.defaultLocale())));
            return true;
        }
        if (!player.hasPermission(CorePermissions.COMMAND_COINS)) {
            player.sendMessage(locale.render("general.no-permission", player.locale()));
            return true;
        }
        Currency currency = economy.primaryCurrency();
        profiles.getCached(player.getUniqueId()).ifPresentOrElse(
                profile -> player.sendMessage(locale.render("economy.balance", player.locale(),
                        Placeholder.unparsed("amount", String.valueOf(profile.balance(currency))),
                        Placeholder.component("currency", CurrencyNames.render(locale, currency, player.locale())))),
                () -> player.sendMessage(locale.render("general.profile-not-loaded", player.locale())));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
