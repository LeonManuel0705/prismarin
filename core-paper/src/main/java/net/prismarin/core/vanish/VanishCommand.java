package net.prismarin.core.vanish;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.vanish.VanishChangedEvent;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class VanishCommand implements TabExecutor {

    private final VanishService vanish;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final CoreConfig config;

    @Inject
    public VanishCommand(VanishService vanish, LocaleService locale, ProfileService profiles, CoreConfig config) {
        this.vanish = vanish;
        this.locale = locale;
        this.profiles = profiles;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Locale senderLocale = sender instanceof Player player ? PlayerLocales.of(player, profiles)
                : Locale.forLanguageTag(config.defaultLocale());
        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(locale.render("general.players-only", senderLocale));
                return true;
            }
            target = player;
        } else {
            if (!sender.hasPermission(VanishService.OTHERS)) {
                sender.sendMessage(locale.render("general.no-permission", senderLocale));
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(locale.render("general.player-not-found", senderLocale,
                        Placeholder.unparsed("player", args[0])));
                return true;
            }
        }
        if (!sender.hasPermission(VanishService.USE)) {
            sender.sendMessage(locale.render("general.no-permission", senderLocale));
            return true;
        }
        boolean nowVanished = !vanish.vanished(target);
        if (nowVanished) {
            vanish.vanish(target, VanishChangedEvent.Cause.COMMAND);
        } else {
            vanish.reveal(target, VanishChangedEvent.Cause.COMMAND);
        }
        Locale targetLocale = PlayerLocales.of(target, profiles);
        target.sendMessage(locale.render(nowVanished ? "vanish.enabled" : "vanish.disabled", targetLocale));
        if (!target.equals(sender)) {
            sender.sendMessage(locale.render(nowVanished ? "vanish.enabled-other" : "vanish.disabled-other",
                    senderLocale, Placeholder.unparsed("player", target.getName())));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(VanishService.OTHERS)) {
            return List.of();
        }
        String input = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input)).sorted().toList();
    }
}
