package net.prismarin.core.player;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.prismarin.api.player.PlayerApi;
import net.prismarin.api.player.PlayerService;
import net.prismarin.core.PrismarinCorePlugin;
import org.bukkit.plugin.ServicePriority;

@Singleton
public final class PaperPlayers {

    private final PrismarinCorePlugin plugin;
    private final PaperPlayerService service;
    private final PlayerTracker tracker;

    @Inject
    public PaperPlayers(PrismarinCorePlugin plugin, PaperPlayerService service, PlayerTracker tracker) {
        this.plugin = plugin;
        this.service = service;
        this.tracker = tracker;
    }

    public PlayerService enable() {
        plugin.getServer().getPluginManager().registerEvents(tracker, plugin);
        tracker.start();
        PlayerApi.register(service);
        plugin.getServer().getServicesManager().register(PlayerService.class, service, plugin, ServicePriority.Normal);
        return service;
    }

    public void disable() {
        tracker.stop();
        PlayerApi.unregister();
    }
}
