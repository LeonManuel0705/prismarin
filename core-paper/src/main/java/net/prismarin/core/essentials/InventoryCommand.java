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
public final class InventoryCommand implements TabExecutor {

    private final EssentialsSupport support;
    private final InventoryWatch watch;

    @Inject
    public InventoryCommand(EssentialsSupport support, InventoryWatch watch) {
        this.support = support;
        this.watch = watch;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean enderChest = command.getName().equalsIgnoreCase("enderchest");
        EssentialsFeature feature = enderChest ? EssentialsFeature.ENDERCHEST : EssentialsFeature.INVSEE;
        Optional<Player> self = support.self(sender);
        if (self.isEmpty() || !support.allowed(sender, feature.permission())) {
            return true;
        }
        Player viewer = self.get();
        if (args.length > 1 || !enderChest && args.length == 0) {
            support.send(viewer, "essentials.invsee.usage");
            return true;
        }
        if (enderChest && args.length == 0) {
            watch.open(viewer, viewer.getEnderChest(), true);
            return true;
        }
        if (enderChest && !support.allowed(viewer, feature.permission("others"))) {
            return true;
        }
        Optional<Player> target = support.find(viewer, args[0]);
        if (target.isEmpty()) {
            support.send(viewer, "general.player-not-found", Placeholder.unparsed("player", args[0]));
            return true;
        }
        Player owner = target.get();
        if (owner.equals(viewer)) {
            if (enderChest) {
                watch.open(viewer, viewer.getEnderChest(), true);
            } else {
                support.send(viewer, "essentials.invsee.self");
            }
            return true;
        }
        boolean editable = viewer.hasPermission(feature.permission("modify"));
        watch.open(viewer, enderChest ? owner.getEnderChest() : owner.getInventory(), editable);
        String key = "essentials." + feature.key() + (editable ? ".opened" : ".view-only");
        support.send(viewer, key, Placeholder.unparsed("player", owner.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean enderChest = command.getName().equalsIgnoreCase("enderchest");
        if (args.length != 1 || enderChest && !sender.hasPermission(EssentialsFeature.ENDERCHEST.permission("others"))) {
            return List.of();
        }
        return support.names(sender, args[0]);
    }
}
