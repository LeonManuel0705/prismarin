package net.prismarin.core.nick;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.nick.NickResult;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class NickCommand implements TabExecutor {

    private static final String AUTO = "auto";

    private final NickManager nicks;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final CoreConfig config;

    @Inject
    public NickCommand(NickManager nicks, LocaleService locale, ProfileService profiles, CoreConfig config) {
        this.nicks = nicks;
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
        Locale target = PlayerLocales.of(player, profiles);
        if (!nicks.running()) {
            player.sendMessage(locale.render("nick.disabled", target));
            return true;
        }
        if (profiles.getCached(player.getUniqueId()).isEmpty()) {
            player.sendMessage(locale.render("general.profile-not-loaded", target));
            return true;
        }
        if (args.length > 1) {
            player.sendMessage(locale.render("nick.usage", target));
            return true;
        }
        if (args.length == 0) {
            if (!player.hasPermission(NickManager.PERMISSION_USE)) {
                player.sendMessage(locale.render("general.no-permission", target));
                return true;
            }
            nicks.nickRandom(player.getUniqueId()).thenAccept(result -> report(player, result));
            return true;
        }
        if (args[0].equalsIgnoreCase(AUTO)) {
            toggleAuto(player, target);
            return true;
        }
        if (!player.hasPermission(NickManager.PERMISSION_CUSTOM)) {
            player.sendMessage(locale.render("general.no-permission", target));
            return true;
        }
        nicks.nick(player.getUniqueId(), args[0]).thenAccept(result -> report(player, result));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(NickManager.PERMISSION_AUTO)) {
            return List.of();
        }
        return AUTO.startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of(AUTO) : List.of();
    }

    private void toggleAuto(Player player, Locale target) {
        if (!player.hasPermission(NickManager.PERMISSION_AUTO)) {
            player.sendMessage(locale.render("general.no-permission", target));
            return;
        }
        boolean enabled = !nicks.autoNick(player.getUniqueId());
        nicks.setAutoNick(player.getUniqueId(), enabled).whenComplete((unused, error) -> {
            player.sendMessage(locale.render(error != null ? "nick.failed"
                    : enabled ? "nick.auto-on" : "nick.auto-off", target));
            if (error == null && enabled && !nicks.autoNickUsedHere()) {
                player.sendMessage(locale.render("nick.auto-not-here", target));
            }
        });
    }

    private void report(Player player, NickResult result) {
        Locale target = PlayerLocales.of(player, profiles);
        String key = switch (result.status()) {
            case APPLIED -> "nick.applied";
            case STORED -> "nick.stored";
            case INVALID_NAME -> "nick.invalid-name";
            case NAME_TAKEN -> "nick.name-taken";
            case NO_FREE_NAME -> "nick.no-free-name";
            case BUSY -> "nick.busy";
            case DISABLED -> "nick.disabled";
            default -> "nick.failed";
        };
        player.sendMessage(locale.render(key, target, Placeholder.unparsed("nick", result.name())));
    }
}
