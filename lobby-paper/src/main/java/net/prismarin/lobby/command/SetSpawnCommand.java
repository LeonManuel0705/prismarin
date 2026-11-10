package net.prismarin.lobby.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.config.LobbySettings;
import net.prismarin.lobby.config.LocationsFile;
import net.prismarin.lobby.config.SpawnPoint;
import net.prismarin.lobby.player.LobbyPlayerSetup;
import net.prismarin.lobby.world.LobbyWorlds;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class SetSpawnCommand implements TabExecutor {

    public static final String PERMISSION = "prismarin.lobby.setspawn";
    private static final List<String> CLEAR = List.of("clear", "remove");

    private final LobbyPlugin plugin;
    private final LocationsFile locations;
    private final LobbyWorlds worlds;
    private final LobbySettings settings;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final ExecutorService executor;
    private final CoreConfig config;
    private final LobbyPlayerSetup setup;

    @Inject
    public SetSpawnCommand(LobbyPlugin plugin, LocationsFile locations, LobbyWorlds worlds, LobbySettings settings,
                           LocaleService locale, ProfileService profiles, ExecutorService executor,
                           CoreConfig config, LobbyPlayerSetup setup) {
        this.plugin = plugin;
        this.locations = locations;
        this.worlds = worlds;
        this.settings = settings;
        this.locale = locale;
        this.profiles = profiles;
        this.executor = executor;
        this.config = config;
        this.setup = setup;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(locale.render("general.players-only",
                    Locale.forLanguageTag(config.defaultLocale())));
            return true;
        }
        Locale playerLocale = PlayerLocales.of(player, profiles);
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(locale.render("general.no-permission", playerLocale));
            return true;
        }
        String world = player.getWorld().getName();
        if (args.length > 0 && CLEAR.contains(args[0].toLowerCase(Locale.ROOT))) {
            if (!locations.removeSpawn(world)) {
                player.sendMessage(locale.render("lobby.setspawn.none", playerLocale,
                        Placeholder.unparsed("world", world)));
                return true;
            }
            refreshWorld(player.getWorld());
            save(player, locale.render("lobby.setspawn.cleared", playerLocale, Placeholder.unparsed("world", world)),
                    playerLocale);
            return true;
        }
        Location location = player.getLocation();
        SpawnPoint point = new SpawnPoint(world, location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
        locations.spawn(point);
        refreshWorld(player.getWorld());
        save(player, locale.render("lobby.setspawn.success", playerLocale,
                Placeholder.unparsed("x", coordinate(point.x())),
                Placeholder.unparsed("y", coordinate(point.y())),
                Placeholder.unparsed("z", coordinate(point.z())),
                Placeholder.unparsed("world", world)), playerLocale);
        OptionalDouble voidHeight = locations.voidHeight(world);
        if (settings.enabled(LobbySettings.VOID_TELEPORT) && voidHeight.isEmpty()) {
            player.sendMessage(locale.render("lobby.setspawn.void-hint", playerLocale));
        } else if (voidHeight.isPresent() && point.y() < voidHeight.getAsDouble()) {
            player.sendMessage(locale.render("lobby.setspawn.below-void", playerLocale,
                    Placeholder.unparsed("y", coordinate(voidHeight.getAsDouble()))));
        }
        return true;
    }

    private void refreshWorld(World world) {
        boolean before = worlds.isLobbyWorld(world);
        worlds.refresh();
        boolean after = worlds.isLobbyWorld(world);
        if (before == after) {
            return;
        }
        for (Player inside : world.getPlayers()) {
            if (after) {
                setup.enter(inside, false);
            } else {
                setup.leave(inside);
            }
        }
    }

    private void save(Player player, Component success, Locale playerLocale) {
        CompletableFuture.runAsync(locations::save, executor)
                .whenComplete((unused, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        plugin.getLogger().warning("Could not save locations.yml: " + error.getMessage());
                        player.sendMessage(locale.render("lobby.setspawn.failed", playerLocale));
                        return;
                    }
                    player.sendMessage(success);
                }));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return CLEAR.stream().limit(1).filter(option -> option.startsWith(prefix)).toList();
    }

    static String coordinate(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
