package net.prismarin.core.essentials;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class ClearInventoryCommand implements TabExecutor {

    private final EssentialsSupport support;

    @Inject
    public ClearInventoryCommand(EssentialsSupport support) {
        this.support = support;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!support.allowed(sender, EssentialsFeature.CLEARINVENTORY.permission())) {
            return true;
        }
        Optional<Player> target = support.target(sender, args, 0,
                EssentialsFeature.CLEARINVENTORY.permission("others"));
        if (target.isEmpty()) {
            return true;
        }
        Player player = target.get();
        player.getInventory().clear();
        player.setItemOnCursor(null);
        support.send(player, "essentials.clear.self");
        if (!EssentialsSupport.same(sender, player)) {
            support.send(sender, "essentials.clear.other", Placeholder.unparsed("player", player.getName()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(EssentialsFeature.CLEARINVENTORY.permission("others"))) {
            return List.of();
        }
        return support.names(sender, args[0]);
    }
}
