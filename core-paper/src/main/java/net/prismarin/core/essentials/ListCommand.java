package net.prismarin.core.essentials;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.server.ServerStatus;
import net.prismarin.shared.status.NetworkStatusService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class ListCommand implements TabExecutor {

    private final EssentialsSupport support;
    private final NetworkStatusService status;

    @Inject
    public ListCommand(EssentialsSupport support, NetworkStatusService status) {
        this.support = support;
        this.status = status;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!support.allowed(sender, EssentialsFeature.LIST.permission())) {
            return true;
        }
        List<Component> names = new ArrayList<>();
        int visible = 0;
        List<Player> players = new ArrayList<>(support.plugin().getServer().getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        for (Player player : players) {
            boolean vanished = support.vanish().vanished(player);
            if (!vanished) {
                visible++;
                names.add(Component.text(player.getName(), NamedTextColor.WHITE));
            } else if (support.canSee(sender, player)) {
                names.add(Component.text(player.getName() + " (V)", NamedTextColor.GRAY)
                        .decorate(TextDecoration.ITALIC));
            }
        }
        support.send(sender, "essentials.list.here", Placeholder.unparsed("count", String.valueOf(visible)),
                Placeholder.unparsed("max", String.valueOf(support.plugin().getServer().getMaxPlayers())));
        if (!names.isEmpty()) {
            sender.sendMessage(Component.text(" ").append(Component.join(
                    JoinConfiguration.separator(Component.text(", ", NamedTextColor.DARK_GRAY)), names)));
        }
        int network = status.statuses().stream().filter(ServerStatus::online).mapToInt(ServerStatus::players).sum();
        if (network > 0) {
            support.send(sender, "essentials.list.network", Placeholder.unparsed("count", String.valueOf(network)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
