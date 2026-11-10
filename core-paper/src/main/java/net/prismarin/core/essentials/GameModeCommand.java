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
public final class GameModeCommand implements TabExecutor {

    private static final String GENERIC = "gm";

    private final EssentialsSupport support;

    @Inject
    public GameModeCommand(EssentialsSupport support) {
        this.support = support;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!support.allowed(sender, EssentialsFeature.GAMEMODE.permission())) {
            return true;
        }
        boolean generic = command.getName().equalsIgnoreCase(GENERIC);
        Optional<GameModeChoice> choice = generic
                ? (args.length == 0 ? Optional.empty() : GameModeChoice.parse(args[0]))
                : GameModeChoice.parse(command.getName());
        int targetIndex = generic ? 1 : 0;
        if (choice.isEmpty() || args.length > targetIndex + 1) {
            support.send(sender, "essentials.gamemode.usage");
            return true;
        }
        Optional<Player> target = support.target(sender, args, targetIndex,
                EssentialsFeature.GAMEMODE.permission("others"));
        if (target.isEmpty()) {
            return true;
        }
        Player player = target.get();
        player.setGameMode(GameMode.valueOf(choice.get().name()));
        String mode = "essentials.gamemode.mode." + choice.get().key();
        support.send(player, "essentials.gamemode.changed",
                Placeholder.component("mode", support.render(player, mode)));
        if (!EssentialsSupport.same(sender, player)) {
            support.send(sender, "essentials.gamemode.changed-other", Placeholder.unparsed("player", player.getName()),
                    Placeholder.component("mode", support.render(sender, mode)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean generic = command.getName().equalsIgnoreCase(GENERIC);
        if (generic && args.length == 1) {
            return EssentialsSupport.filter(GameModeChoice.SUGGESTIONS, args[0]);
        }
        int targetIndex = generic ? 1 : 0;
        if (args.length != targetIndex + 1 || !sender.hasPermission(EssentialsFeature.GAMEMODE.permission("others"))) {
            return List.of();
        }
        return support.names(sender, args[targetIndex]);
    }
}
