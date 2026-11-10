package net.prismarin.core.essentials;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

@Singleton
public final class TeleportCommand implements TabExecutor {

    private final EssentialsSupport support;

    @Inject
    public TeleportCommand(EssentialsSupport support) {
        this.support = support;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Optional<Player> self = support.self(sender);
        if (self.isEmpty()) {
            return true;
        }
        Player player = self.get();
        boolean all = command.getName().equalsIgnoreCase("tpall");
        if (!support.allowed(player, (all ? EssentialsFeature.TPALL : EssentialsFeature.TPHERE).permission())) {
            return true;
        }
        Location destination = player.getLocation();
        if (all) {
            int count = 0;
            for (Player other : support.plugin().getServer().getOnlinePlayers()) {
                if (other.equals(player) || support.vanish().vanished(other) || !support.canSee(player, other)) {
                    continue;
                }
                pull(player, other, destination);
                count++;
            }
            support.send(player, count == 0 ? "essentials.teleport.nobody" : "essentials.teleport.all",
                    Placeholder.unparsed("count", String.valueOf(count)));
            return true;
        }
        if (args.length != 1) {
            support.send(player, "essentials.teleport.usage");
            return true;
        }
        Optional<Player> target = support.find(player, args[0]);
        if (target.isEmpty()) {
            support.send(player, "general.player-not-found", Placeholder.unparsed("player", args[0]));
            return true;
        }
        if (target.get().equals(player)) {
            support.send(player, "essentials.teleport.self");
            return true;
        }
        pull(player, target.get(), destination);
        support.send(player, "essentials.teleport.here", Placeholder.unparsed("player", target.get().getName()));
        return true;
    }

    private void pull(Player actor, Player target, Location destination) {
        target.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.COMMAND);
        if (support.vanish().canSee(target, actor)) {
            support.send(target, "essentials.teleport.pulled", Placeholder.unparsed("player", actor.getName()));
        } else {
            support.send(target, "essentials.teleport.pulled-hidden");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("tpall") || args.length != 1) {
            return List.of();
        }
        return support.names(sender, args[0]);
    }
}
