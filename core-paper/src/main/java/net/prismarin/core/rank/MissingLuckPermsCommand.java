package net.prismarin.core.rank;

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

public final class MissingLuckPermsCommand implements TabExecutor {

    private final LocaleService locale;
    private final ProfileService profiles;
    private final CoreConfig config;

    public MissingLuckPermsCommand(LocaleService locale, ProfileService profiles, CoreConfig config) {
        this.locale = locale;
        this.profiles = profiles;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Locale target = sender instanceof Player player ? PlayerLocales.of(player, profiles)
                : Locale.forLanguageTag(config.defaultLocale());
        sender.sendMessage(locale.render("rank.no-luckperms", target));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
