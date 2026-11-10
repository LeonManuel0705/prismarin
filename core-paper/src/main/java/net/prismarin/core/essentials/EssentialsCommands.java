package net.prismarin.core.essentials;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.prismarin.core.PrismarinCorePlugin;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;

@Singleton
public final class EssentialsCommands {

    private static final Map<EssentialsFeature, Class<? extends TabExecutor>> EXECUTORS = Map.ofEntries(
            Map.entry(EssentialsFeature.TPALL, TeleportCommand.class),
            Map.entry(EssentialsFeature.TPHERE, TeleportCommand.class),
            Map.entry(EssentialsFeature.HEAL, RestoreCommand.class),
            Map.entry(EssentialsFeature.FEED, RestoreCommand.class),
            Map.entry(EssentialsFeature.FLY, FlyCommand.class),
            Map.entry(EssentialsFeature.GAMEMODE, GameModeCommand.class),
            Map.entry(EssentialsFeature.SPEED, SpeedCommand.class),
            Map.entry(EssentialsFeature.CLEARINVENTORY, ClearInventoryCommand.class),
            Map.entry(EssentialsFeature.INVSEE, InventoryCommand.class),
            Map.entry(EssentialsFeature.ENDERCHEST, InventoryCommand.class),
            Map.entry(EssentialsFeature.TIME, WorldCommand.class),
            Map.entry(EssentialsFeature.WEATHER, WorldCommand.class),
            Map.entry(EssentialsFeature.PING, PingCommand.class),
            Map.entry(EssentialsFeature.HAT, HatCommand.class),
            Map.entry(EssentialsFeature.SKULL, SkullCommand.class));

    private final PrismarinCorePlugin plugin;
    private final Injector injector;

    @Inject
    public EssentialsCommands(PrismarinCorePlugin plugin, Injector injector) {
        this.plugin = plugin;
        this.injector = injector;
    }

    public List<EssentialsFeature> install(boolean moduleEnabled) {
        EssentialsSettings settings = moduleEnabled
                ? EssentialsSettings.load(plugin.getDataFolder().toPath().resolve("config.yml"))
                : new EssentialsSettings(Map.of());
        List<EssentialsFeature> active = new ArrayList<>();
        for (EssentialsFeature feature : EssentialsFeature.values()) {
            boolean enabled = moduleEnabled && settings.enabled(feature);
            for (String name : feature.commands()) {
                PluginCommand command = plugin.getCommand(name);
                if (command == null) {
                    throw new IllegalStateException("command " + name + " is missing from plugin.yml");
                }
                if (enabled) {
                    TabExecutor executor = injector.getInstance(EXECUTORS.get(feature));
                    command.setExecutor(executor);
                    command.setTabCompleter(executor);
                } else {
                    command.unregister(plugin.getServer().getCommandMap());
                    plugin.getServer().getCommandMap().getKnownCommands().values().removeIf(known -> known == command);
                }
            }
            if (enabled) {
                active.add(feature);
            }
        }
        if (active.contains(EssentialsFeature.INVSEE) || active.contains(EssentialsFeature.ENDERCHEST)) {
            plugin.getServer().getPluginManager().registerEvents(injector.getInstance(InventoryWatch.class), plugin);
            plugin.getServer().getPluginManager().registerEvents(injector.getInstance(FlightGrants.class), plugin);
        }
        return List.copyOf(active);
    }
}
