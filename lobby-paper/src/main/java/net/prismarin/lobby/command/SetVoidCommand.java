package net.prismarin.lobby.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
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
import net.prismarin.lobby.config.LocationsFile;
import net.prismarin.lobby.world.LobbyWorlds;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class SetVoidCommand implements TabExecutor {

    public static final String PERMISSION = "prismarin.lobby.setvoid";
    private static final String OFF = "off";
    private static final String HERE = "here";

    private final LobbyPlugin plugin;
    private final LocationsFile locations;
    private final LobbyWorlds worlds;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final ExecutorService executor;
    private final CoreConfig config;

    @Inject
    public SetVoidCommand(LobbyPlugin plugin, LocationsFile locations, LobbyWorlds worlds, LocaleService locale,
                          ProfileService profiles, ExecutorService executor, CoreConfig config) {
        this.plugin = plugin;
        this.locations = locations;
        this.worlds = worlds;
        this.locale = locale;
        this.profiles = profiles;
        this.executor = executor;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Locale senderLocale = localeOf(sender);
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(locale.render("general.no-permission", senderLocale));
            return true;
        }
        World world;
        if (args.length >= 2) {
            world = plugin.getServer().getWorld(args[1]);
            if (world == null) {
                sender.sendMessage(locale.render("lobby.setvoid.unknown-world", senderLocale,
                        Placeholder.unparsed("world", args[1])));
                return true;
            }
        } else if (sender instanceof Player player) {
            world = player.getWorld();
        } else {
            sender.sendMessage(locale.render("lobby.setvoid.usage", senderLocale));
            return true;
        }
        String worldName = world.getName();
        if (args.length == 0) {
            OptionalDouble current = locations.voidHeight(worldName);
            sender.sendMessage(current.isPresent()
                    ? locale.render("lobby.setvoid.status-on", senderLocale, Placeholder.unparsed("world", worldName),
                            Placeholder.unparsed("y", SetSpawnCommand.coordinate(current.getAsDouble())))
                    : locale.render("lobby.setvoid.status-off", senderLocale,
                            Placeholder.unparsed("world", worldName)));
            sender.sendMessage(locale.render("lobby.setvoid.usage", senderLocale));
            return true;
        }
        String value = args[0].toLowerCase(Locale.ROOT);
        OptionalDouble height;
        if (value.equals(OFF)) {
            height = OptionalDouble.empty();
        } else if (value.equals(HERE) && sender instanceof Player player && player.getWorld().equals(world)) {
            height = OptionalDouble.of(Math.floor(player.getLocation().getY()));
        } else {
            try {
                double parsed = Double.parseDouble(value);
                if (!Double.isFinite(parsed)) {
                    throw new NumberFormatException(value);
                }
                height = OptionalDouble.of(parsed);
            } catch (NumberFormatException e) {
                sender.sendMessage(locale.render("lobby.setvoid.invalid", senderLocale,
                        Placeholder.unparsed("value", args[0])));
                return true;
            }
        }
        locations.voidHeight(worldName, height);
        Component reply = height.isPresent()
                ? locale.render("lobby.setvoid.set", senderLocale, Placeholder.unparsed("world", worldName),
                        Placeholder.unparsed("y", SetSpawnCommand.coordinate(height.getAsDouble())))
                : locale.render("lobby.setvoid.disabled", senderLocale, Placeholder.unparsed("world", worldName));
        List<Component> hints = new ArrayList<>();
        if (height.isPresent() && worlds.spawn(world).isEmpty() && locations.spawns().isEmpty()) {
            hints.add(locale.render("lobby.setvoid.no-spawn", senderLocale));
        }
        if (height.isPresent() && worlds.spawn(world).filter(spawn -> spawn.getY() < height.getAsDouble())
                .isPresent()) {
            hints.add(locale.render("lobby.setvoid.spawn-below", senderLocale, Placeholder.unparsed("world", worldName),
                    Placeholder.unparsed("y", SetSpawnCommand.coordinate(height.getAsDouble()))));
        }
        if (height.isPresent() && !worlds.isLobbyWorld(world)) {
            hints.add(locale.render("lobby.setvoid.not-lobby-world", senderLocale,
                    Placeholder.unparsed("world", worldName)));
        }
        CompletableFuture.runAsync(locations::save, executor)
                .whenComplete((unused, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (sender instanceof Player player && !player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        plugin.getLogger().warning("Could not save locations.yml: " + error.getMessage());
                        sender.sendMessage(locale.render("lobby.setvoid.failed", senderLocale));
                        return;
                    }
                    sender.sendMessage(reply);
                    hints.forEach(sender::sendMessage);
                }));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of(OFF));
            if (sender instanceof Player player) {
                options.add(HERE);
                options.add(String.valueOf(player.getWorld().getMinHeight()));
            }
            options.add("0");
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return options.stream().distinct().filter(option -> option.startsWith(prefix)).toList();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return plugin.getServer().getWorlds().stream()
                    .map(World::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private Locale localeOf(CommandSender sender) {
        return sender instanceof Player player
                ? PlayerLocales.of(player, profiles)
                : Locale.forLanguageTag(config.defaultLocale());
    }
}
