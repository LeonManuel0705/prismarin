package net.prismarin.core.nick;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class RealnameCommand implements TabExecutor {

    private final NickManager nicks;
    private final NickRegistry registry;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final CoreConfig config;

    @Inject
    public RealnameCommand(NickManager nicks, NickRegistry registry, LocaleService locale, ProfileService profiles,
                           CoreConfig config) {
        this.nicks = nicks;
        this.registry = registry;
        this.locale = locale;
        this.profiles = profiles;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Locale target = sender instanceof Player player ? PlayerLocales.of(player, profiles)
                : Locale.forLanguageTag(config.defaultLocale());
        if (!sender.hasPermission(NickManager.PERMISSION_REALNAME)) {
            sender.sendMessage(locale.render("general.no-permission", target));
            return true;
        }
        if (args.length == 0) {
            list(sender, target);
            return true;
        }
        if (args.length > 1) {
            sender.sendMessage(locale.render("nick.realname.usage", target));
            return true;
        }
        String nick = args[0];
        Optional<UUID> here = registry.byNick(nick);
        if (here.isPresent()) {
            Optional<NickRegistry.Shown> shown = registry.shown(here.get());
            if (shown.isPresent()) {
                sender.sendMessage(locale.render("nick.realname.result", target,
                        Placeholder.unparsed("nick", shown.get().nick()),
                        Placeholder.unparsed("name", shown.get().realName())));
                return true;
            }
        }
        nicks.lookupClaim(nick)
                .thenCompose(claim -> claim.map(found -> profiles.find(found.owner())
                                .thenApply(profile -> profile.map(real -> new Remote(found.name(), real.getName(),
                                        found.server()))))
                        .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())))
                .whenComplete((remote, error) -> {
                    if (error != null || remote.isEmpty()) {
                        sender.sendMessage(locale.render("nick.realname.unknown", target,
                                Placeholder.unparsed("nick", nick)));
                        return;
                    }
                    sender.sendMessage(locale.render("nick.realname.result-remote", target,
                            Placeholder.unparsed("nick", remote.get().nick()),
                            Placeholder.unparsed("name", remote.get().name()),
                            Placeholder.unparsed("server", remote.get().server())));
                });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(NickManager.PERMISSION_REALNAME)) {
            return List.of();
        }
        String input = args[0].toLowerCase(Locale.ROOT);
        return registry.all().values().stream()
                .map(NickRegistry.Shown::nick)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                .sorted()
                .toList();
    }

    private void list(CommandSender sender, Locale target) {
        Map<UUID, NickRegistry.Shown> all = registry.all();
        if (all.isEmpty()) {
            sender.sendMessage(locale.render("nick.realname.list-empty", target));
            return;
        }
        sender.sendMessage(locale.render("nick.realname.list-header", target,
                Placeholder.unparsed("count", String.valueOf(all.size()))));
        all.values().stream()
                .sorted(Comparator.comparing(shown -> shown.nick().toLowerCase(Locale.ROOT)))
                .forEach(shown -> sender.sendMessage(locale.render("nick.realname.list-entry", target,
                        Placeholder.unparsed("nick", shown.nick()), Placeholder.unparsed("name", shown.realName()))));
    }

    private record Remote(String nick, String name, String server) {
    }
}
