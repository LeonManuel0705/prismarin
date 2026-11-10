package net.prismarin.core.essentials;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class PingCommand implements TabExecutor {

    private static final int GOOD = 80;
    private static final int FAIR = 160;

    private final EssentialsSupport support;

    @Inject
    public PingCommand(EssentialsSupport support) {
        this.support = support;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!support.allowed(sender, EssentialsFeature.PING.permission())) {
            return true;
        }
        if (args.length == 0 && !(sender instanceof Player)) {
            support.send(sender, "essentials.ping.usage");
            return true;
        }
        Optional<Player> target = support.target(sender, args, 0, EssentialsFeature.PING.permission("others"));
        if (target.isEmpty()) {
            return true;
        }
        Player player = target.get();
        Component ping = support.render(sender, "essentials.ping." + quality(player.getPing()),
                Placeholder.unparsed("ms", String.valueOf(player.getPing())));
        if (EssentialsSupport.same(sender, player)) {
            support.send(sender, "essentials.ping.self", Placeholder.component("ping", ping));
        } else {
            support.send(sender, "essentials.ping.other", Placeholder.unparsed("player", player.getName()),
                    Placeholder.component("ping", ping));
        }
        return true;
    }

    static String quality(int ping) {
        return ping < GOOD ? "good" : ping < FAIR ? "fair" : "poor";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(EssentialsFeature.PING.permission("others"))) {
            return List.of();
        }
        return support.names(sender, args[0]);
    }
}
