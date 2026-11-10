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
public final class SpeedCommand implements TabExecutor {

    private static final List<String> LEVELS = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
            SpeedLevels.RESET);

    private final EssentialsSupport support;

    @Inject
    public SpeedCommand(EssentialsSupport support) {
        this.support = support;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!support.allowed(sender, EssentialsFeature.SPEED.permission())) {
            return true;
        }
        Optional<Integer> level = args.length == 0 || args.length > 2 ? Optional.empty() : SpeedLevels.parse(args[0]);
        if (level.isEmpty()) {
            support.send(sender, "essentials.speed.usage");
            return true;
        }
        Optional<Player> target = support.target(sender, args, 1, EssentialsFeature.SPEED.permission("others"));
        if (target.isEmpty()) {
            return true;
        }
        Player player = target.get();
        boolean flying = player.isFlying();
        float value = SpeedLevels.value(level.get(), flying);
        if (flying) {
            player.setFlySpeed(value);
        } else {
            player.setWalkSpeed(value);
        }
        String shown = String.valueOf(level.get());
        String key = flying ? "essentials.speed.fly" : "essentials.speed.walk";
        support.send(player, key, Placeholder.unparsed("speed", shown));
        if (!EssentialsSupport.same(sender, player)) {
            support.send(sender, key + "-other", Placeholder.unparsed("player", player.getName()),
                    Placeholder.unparsed("speed", shown));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return EssentialsSupport.filter(LEVELS, args[0]);
        }
        if (args.length != 2 || !sender.hasPermission(EssentialsFeature.SPEED.permission("others"))) {
            return List.of();
        }
        return support.names(sender, args[1]);
    }
}
