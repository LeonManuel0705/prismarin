package net.prismarin.lobby.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.lobby.item.LobbyItems;
import net.prismarin.lobby.player.BuildModeService;
import net.prismarin.lobby.player.PlayerHiderService;
import net.prismarin.lobby.player.PlayerLocales;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class LobbyBuildCommand implements TabExecutor {

    private final BuildModeService buildMode;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final LobbyItems items;
    private final PlayerHiderService hider;

    @Inject
    public LobbyBuildCommand(BuildModeService buildMode, LocaleService locale, ProfileService profiles,
                             LobbyItems items, PlayerHiderService hider) {
        this.buildMode = buildMode;
        this.locale = locale;
        this.profiles = profiles;
        this.items = items;
        this.hider = hider;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(locale.render("general.players-only", Locale.GERMAN));
            return true;
        }
        Locale playerLocale = PlayerLocales.of(player, profiles);
        if (buildMode.toggle(player)) {
            player.setGameMode(GameMode.CREATIVE);
            player.sendMessage(locale.render("lobby.build.enabled", playerLocale));
        } else {
            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(true);
            items.give(player, hider.hiding(player));
            player.sendMessage(locale.render("lobby.build.disabled", playerLocale));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
