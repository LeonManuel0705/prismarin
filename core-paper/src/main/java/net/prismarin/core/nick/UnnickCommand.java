package net.prismarin.core.nick;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
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
public final class UnnickCommand implements TabExecutor {

    private final NickManager nicks;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final CoreConfig config;

    @Inject
    public UnnickCommand(NickManager nicks, LocaleService locale, ProfileService profiles, CoreConfig config) {
        this.nicks = nicks;
        this.locale = locale;
        this.profiles = profiles;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Locale senderLocale = sender instanceof Player player ? PlayerLocales.of(player, profiles)
                : Locale.forLanguageTag(config.defaultLocale());
        if (!nicks.running()) {
            sender.sendMessage(locale.render("nick.disabled", senderLocale));
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(locale.render("general.players-only", senderLocale));
                return true;
            }
            if (NickManager.NICK_PERMISSIONS.stream().noneMatch(player::hasPermission)) {
                sender.sendMessage(locale.render("general.no-permission", senderLocale));
                return true;
            }
            nicks.unnick(player.getUniqueId()).thenAccept(result -> {
                sender.sendMessage(locale.render(ownResultKey(result), senderLocale,
                        Placeholder.unparsed("name", result.name())));
                if (result.status() == NickResult.Status.REMOVED && nicks.autoNick(player.getUniqueId())) {
                    sender.sendMessage(locale.render("nick.auto-still-on", senderLocale));
                }
            });
            return true;
        }
        if (!sender.hasPermission(NickManager.PERMISSION_OTHERS)) {
            sender.sendMessage(locale.render("general.no-permission", senderLocale));
            return true;
        }
        Optional<Player> target = nicks.findOnline(args[0]);
        if (target.isEmpty()) {
            sender.sendMessage(locale.render("general.player-not-found", senderLocale,
                    Placeholder.unparsed("player", args[0])));
            return true;
        }
        Player player = target.get();
        String shownName = player.getName();
        UUID uuid = player.getUniqueId();
        nicks.unnick(uuid).thenAccept(result -> {
            if (result.status() == NickResult.Status.REMOVED) {
                sender.sendMessage(locale.render("nick.others.removed", senderLocale,
                        Placeholder.unparsed("player", shownName), Placeholder.unparsed("name", result.name())));
                if (player.isOnline() && !player.equals(sender)) {
                    player.sendMessage(locale.render("nick.removed-by-staff", PlayerLocales.of(player, profiles),
                            Placeholder.unparsed("name", result.name())));
                }
                return;
            }
            sender.sendMessage(locale.render(result.status() == NickResult.Status.NOT_NICKED
                    ? "nick.others.not-nicked" : "nick.failed", senderLocale,
                    Placeholder.unparsed("player", shownName)));
        });
        return true;
    }

    private static String ownResultKey(NickResult result) {
        return switch (result.status()) {
            case REMOVED -> "nick.removed";
            case NOT_NICKED -> "nick.not-nicked";
            case BUSY -> "nick.busy";
            default -> "nick.failed";
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(NickManager.PERMISSION_OTHERS)) {
            return List.of();
        }
        String input = args[0].toLowerCase(Locale.ROOT);
        return nicks.nickedPlayers().stream()
                .map(nicks::shownNick)
                .flatMap(Optional::stream)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                .sorted()
                .toList();
    }
}
