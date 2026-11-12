package net.prismarin.core.network;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.queue.QueueResult;
import net.prismarin.api.queue.QueueStatus;
import net.prismarin.api.queue.QueueTarget;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerGroup;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.status.QueueingConnector;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class PlayCommand implements TabExecutor {

    private final PrismarinCorePlugin plugin;
    private final QueueingConnector connector;
    private final ServerDirectory directory;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final CoreConfig config;

    @Inject
    public PlayCommand(PrismarinCorePlugin plugin, QueueingConnector connector, ServerDirectory directory,
                       LocaleService locale, ProfileService profiles, CoreConfig config) {
        this.plugin = plugin;
        this.connector = connector;
        this.directory = directory;
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
        if (args.length != 1) {
            player.sendMessage(locale.render("play.usage", target));
            return true;
        }
        String group = args[0].toLowerCase(Locale.ROOT);
        if (directory.byGroup(group).isEmpty()) {
            player.sendMessage(locale.render("play.unknown-group", target, Placeholder.unparsed("group", group)));
            return true;
        }
        connector.travel(player.getUniqueId(), QueueTarget.group(group)).thenAccept(result -> plugin.getServer()
                .getScheduler().runTask(plugin, () -> answer(player, target, group, result)));
        return true;
    }

    private void answer(Player player, Locale target, String group, QueueResult result) {
        if (!player.isOnline() || result.waiting() || result.status() == QueueStatus.NOT_LEADER) {
            return;
        }
        if (result.sent()) {
            String server = result.server().orElse(group);
            player.sendMessage(locale.render("play.connecting", target, Placeholder.component("server",
                    locale.format(directory.byName(server).map(NetworkServer::displayName).orElse(server)))));
            return;
        }
        player.sendMessage(locale.render("play.no-server", target, Placeholder.unparsed("group", group)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String input = args[0].toLowerCase(Locale.ROOT);
        return Stream.concat(directory.groups().stream().map(ServerGroup::name),
                        directory.servers().stream().map(NetworkServer::group))
                .distinct()
                .filter(group -> group.startsWith(input))
                .sorted()
                .toList();
    }
}
