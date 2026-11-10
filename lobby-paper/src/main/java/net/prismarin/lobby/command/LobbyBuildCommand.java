package net.prismarin.lobby.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.listener.VoidTeleportListener;
import net.prismarin.lobby.player.BuildModeService;
import net.prismarin.lobby.player.LobbyPlayerSetup;
import net.prismarin.lobby.world.LobbyWorlds;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class LobbyBuildCommand implements TabExecutor {

    private final LobbyPlugin plugin;
    private final BuildModeService buildMode;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final LobbyPlayerSetup setup;
    private final LobbyWorlds worlds;
    private final VoidTeleportListener voidTeleport;
    private final CoreConfig config;

    @Inject
    public LobbyBuildCommand(LobbyPlugin plugin, BuildModeService buildMode, LocaleService locale,
                             ProfileService profiles, LobbyPlayerSetup setup, LobbyWorlds worlds,
                             VoidTeleportListener voidTeleport, CoreConfig config) {
        this.plugin = plugin;
        this.buildMode = buildMode;
        this.locale = locale;
        this.profiles = profiles;
        this.setup = setup;
        this.worlds = worlds;
        this.voidTeleport = voidTeleport;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean self = args.length >= 1 && sender instanceof Player own && args[0].equalsIgnoreCase(own.getName());
        if (args.length >= 1 && !self) {
            takeAway(sender, args[0]);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(locale.render("lobby.build.usage-console", localeOf(sender)));
            return true;
        }
        Locale playerLocale = localeOf(player);
        if (!player.hasPermission(BuildModeService.PERMISSION)) {
            player.sendMessage(locale.render("general.no-permission", playerLocale));
            return true;
        }
        if (buildMode.toggle(player)) {
            player.setGameMode(GameMode.CREATIVE);
            player.sendMessage(locale.render("lobby.build.enabled", playerLocale));
        } else {
            finish(player);
            player.sendMessage(locale.render("lobby.build.disabled", playerLocale));
        }
        return true;
    }

    private void takeAway(CommandSender sender, String name) {
        Locale senderLocale = localeOf(sender);
        if (!sender.hasPermission(BuildModeService.OTHERS_PERMISSION)) {
            sender.sendMessage(locale.render("general.no-permission", senderLocale));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(name);
        if (target == null) {
            sender.sendMessage(locale.render("general.player-not-found", senderLocale,
                    Placeholder.unparsed("player", name)));
            return;
        }
        if (!buildMode.disable(target)) {
            sender.sendMessage(locale.render("lobby.build.not-active", senderLocale,
                    Placeholder.unparsed("player", target.getName())));
            return;
        }
        finish(target);
        if (!Objects.equals(sender, target)) {
            target.sendMessage(locale.render("lobby.build.taken-target", localeOf(target),
                    Placeholder.unparsed("player", sender.getName())));
        }
        sender.sendMessage(locale.render("lobby.build.taken", senderLocale,
                Placeholder.unparsed("player", target.getName())));
    }

    private void finish(Player player) {
        if (!worlds.isLobbyWorld(player.getWorld())) {
            player.setGameMode(plugin.getServer().getDefaultGameMode());
            return;
        }
        setup.enter(player, false);
        voidTeleport.rescueIfFalling(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !sender.hasPermission(BuildModeService.OTHERS_PERMISSION)) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return buildMode.players().stream()
                .map(id -> plugin.getServer().getPlayer(id))
                .filter(Objects::nonNull)
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private Locale localeOf(CommandSender sender) {
        return sender instanceof Player player
                ? PlayerLocales.of(player, profiles)
                : Locale.forLanguageTag(config.defaultLocale());
    }
}
