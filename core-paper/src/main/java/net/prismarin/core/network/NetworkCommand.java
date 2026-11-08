package net.prismarin.core.network;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.permission.CorePermissions;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class NetworkCommand implements TabExecutor {

    private final NetworkMenu menu;
    private final LocaleService locale;
    private final CoreConfig config;
    private final ProfileService profiles;

    @Inject
    public NetworkCommand(NetworkMenu menu, LocaleService locale, CoreConfig config, ProfileService profiles) {
        this.menu = menu;
        this.locale = locale;
        this.config = config;
        this.profiles = profiles;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(locale.render("general.players-only", Locale.forLanguageTag(config.defaultLocale())));
            return true;
        }
        if (!player.hasPermission(CorePermissions.ADMIN_NETWORK)) {
            player.sendMessage(locale.render("general.no-permission", PlayerLocales.of(player, profiles)));
            return true;
        }
        menu.open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
