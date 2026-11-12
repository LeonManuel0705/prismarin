package net.prismarin.core.reward;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class DailyCommand implements TabExecutor {

    public static final String PERMISSION = "prismarin.command.daily";

    private final PaperDailyRewards rewards;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final CoreConfig config;

    @Inject
    public DailyCommand(PaperDailyRewards rewards, LocaleService locale, ProfileService profiles, CoreConfig config) {
        this.rewards = rewards;
        this.locale = locale;
        this.profiles = profiles;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(locale.render("general.players-only", Locale.forLanguageTag(config.defaultLocale())));
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(locale.render("general.no-permission", PlayerLocales.of(player, profiles)));
            return true;
        }
        rewards.claimAndTell(player, null);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
