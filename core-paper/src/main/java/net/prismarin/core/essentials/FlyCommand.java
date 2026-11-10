package net.prismarin.core.essentials;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class FlyCommand implements TabExecutor {

    private final EssentialsSupport support;
    private final FlightGrants grants;

    @Inject
    public FlyCommand(EssentialsSupport support, FlightGrants grants) {
        this.support = support;
        this.grants = grants;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!support.allowed(sender, EssentialsFeature.FLY.permission())) {
            return true;
        }
        Optional<Player> target = support.target(sender, args, 0, EssentialsFeature.FLY.permission("others"));
        if (target.isEmpty()) {
            return true;
        }
        Player player = target.get();
        boolean flying = grants.toggle(player);
        support.vanish().releaseFlight(player);
        boolean creative = player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
        if (flying) {
            player.setAllowFlight(true);
        } else if (!creative) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setFallDistance(0.0F);
        }
        support.send(player, flying ? "essentials.fly.on" : "essentials.fly.off");
        if (!EssentialsSupport.same(sender, player)) {
            support.send(sender, flying ? "essentials.fly.on-other" : "essentials.fly.off-other",
                    Placeholder.unparsed("player", player.getName()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(EssentialsFeature.FLY.permission("others"))) {
            return List.of();
        }
        return support.names(sender, args[0]);
    }
}
