package net.prismarin.lobby.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.config.LocationsFile;
import net.prismarin.lobby.config.SpawnPoint;
import net.prismarin.lobby.player.PlayerLocales;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class SetSpawnCommand implements TabExecutor {

    private final LobbyPlugin plugin;
    private final LocationsFile locations;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final ExecutorService executor;

    @Inject
    public SetSpawnCommand(LobbyPlugin plugin, LocationsFile locations, LocaleService locale,
                           ProfileService profiles, ExecutorService executor) {
        this.plugin = plugin;
        this.locations = locations;
        this.locale = locale;
        this.profiles = profiles;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(locale.render("general.players-only", Locale.GERMAN));
            return true;
        }
        Location location = player.getLocation();
        SpawnPoint point = new SpawnPoint(location.getWorld().getName(), location.getX(), location.getY(),
                location.getZ(), location.getYaw(), location.getPitch());
        locations.spawn(point);
        Locale playerLocale = PlayerLocales.of(player, profiles);
        CompletableFuture.runAsync(locations::save, executor)
                .whenComplete((unused, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        player.sendMessage(locale.render("lobby.setspawn.failed", playerLocale));
                        return;
                    }
                    player.sendMessage(locale.render("lobby.setspawn.success", playerLocale,
                            Placeholder.unparsed("x", coordinate(point.x())),
                            Placeholder.unparsed("y", coordinate(point.y())),
                            Placeholder.unparsed("z", coordinate(point.z())),
                            Placeholder.unparsed("world", point.world())));
                }));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }

    private static String coordinate(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
