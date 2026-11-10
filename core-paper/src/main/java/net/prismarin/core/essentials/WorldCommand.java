package net.prismarin.core.essentials;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class WorldCommand implements TabExecutor {

    private static final long DAY = 1_000L;
    private static final long NIGHT = 13_000L;
    private static final int TICKS_PER_MINUTE = 20 * 60;

    private final EssentialsSupport support;

    @Inject
    public WorldCommand(EssentialsSupport support) {
        this.support = support;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        boolean time = name.equals("day") || name.equals("night");
        EssentialsFeature feature = time ? EssentialsFeature.TIME : EssentialsFeature.WEATHER;
        if (!support.allowed(sender, feature.permission())) {
            return true;
        }
        World world;
        if (args.length >= 1) {
            world = support.plugin().getServer().getWorld(args[0]);
            if (world == null) {
                support.send(sender, "essentials.world.unknown", Placeholder.unparsed("world", args[0]));
                return true;
            }
        } else if (sender instanceof Player player) {
            world = player.getWorld();
        } else {
            support.send(sender, "essentials.world.usage", Placeholder.unparsed("command", name));
            return true;
        }
        switch (name) {
            case "day" -> world.setTime(DAY);
            case "night" -> world.setTime(NIGHT);
            case "sun" -> {
                world.setStorm(false);
                world.setThundering(false);
                world.setClearWeatherDuration(TICKS_PER_MINUTE * 20);
            }
            case "rain" -> {
                world.setStorm(true);
                world.setThundering(false);
                world.setWeatherDuration(TICKS_PER_MINUTE * 10);
            }
            default -> {
                world.setStorm(true);
                world.setThundering(true);
                world.setWeatherDuration(TICKS_PER_MINUTE * 5);
                world.setThunderDuration(TICKS_PER_MINUTE * 5);
            }
        }
        support.send(sender, "essentials.world." + name, Placeholder.unparsed("world", world.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return EssentialsSupport.filter(support.plugin().getServer().getWorlds().stream().map(World::getName).toList(),
                args[0]);
    }
}
