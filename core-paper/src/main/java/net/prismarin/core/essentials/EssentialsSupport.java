package net.prismarin.core.essentials;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.vanish.VanishService;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Singleton
public final class EssentialsSupport {

    private final PrismarinCorePlugin plugin;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final VanishService vanish;
    private final CoreConfig config;

    @Inject
    public EssentialsSupport(PrismarinCorePlugin plugin, LocaleService locale, ProfileService profiles,
                             VanishService vanish, CoreConfig config) {
        this.plugin = plugin;
        this.locale = locale;
        this.profiles = profiles;
        this.vanish = vanish;
        this.config = config;
    }

    public PrismarinCorePlugin plugin() {
        return plugin;
    }

    public VanishService vanish() {
        return vanish;
    }

    public Locale localeOf(CommandSender sender) {
        return sender instanceof Player player ? PlayerLocales.of(player, profiles)
                : Locale.forLanguageTag(config.defaultLocale());
    }

    public Component render(CommandSender viewer, String key, TagResolver... resolvers) {
        return locale.render(key, localeOf(viewer), resolvers);
    }

    public void send(CommandSender viewer, String key, TagResolver... resolvers) {
        viewer.sendMessage(render(viewer, key, resolvers));
    }

    public boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        send(sender, "general.no-permission");
        return false;
    }

    public Optional<Player> self(CommandSender sender) {
        if (sender instanceof Player player) {
            return Optional.of(player);
        }
        send(sender, "general.players-only");
        return Optional.empty();
    }

    public boolean canSee(CommandSender viewer, Player target) {
        return !(viewer instanceof Player player) || vanish.canSee(player, target);
    }

    public Optional<Player> find(CommandSender viewer, String name) {
        Player exact = plugin.getServer().getPlayerExact(name);
        if (exact != null && canSee(viewer, exact)) {
            return Optional.of(exact);
        }
        String prefix = name.toLowerCase(Locale.ROOT);
        List<Player> matches = plugin.getServer().getOnlinePlayers().stream()
                .filter(player -> canSee(viewer, player))
                .filter(player -> player.getName().toLowerCase(Locale.ROOT).startsWith(prefix))
                .map(player -> (Player) player)
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    public Optional<Player> target(CommandSender sender, String[] args, int index, String othersPermission) {
        if (args.length <= index) {
            return self(sender);
        }
        if (!allowed(sender, othersPermission)) {
            return Optional.empty();
        }
        Optional<Player> found = find(sender, args[index]);
        if (found.isEmpty()) {
            send(sender, "general.player-not-found", Placeholder.unparsed("player", args[index]));
        }
        return found;
    }

    public List<String> names(CommandSender viewer, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return plugin.getServer().getOnlinePlayers().stream()
                .filter(player -> canSee(viewer, player))
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public static List<String> filter(List<String> options, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    public static boolean same(CommandSender sender, Player target) {
        return sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
    }
}
