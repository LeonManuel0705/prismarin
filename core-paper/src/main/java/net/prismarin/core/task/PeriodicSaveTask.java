package net.prismarin.core.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.logging.Level;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
import org.bukkit.entity.Player;

@Singleton
public final class PeriodicSaveTask implements Runnable {

    private final PrismarinCorePlugin plugin;
    private final ProfileService profiles;

    @Inject
    public PeriodicSaveTask(PrismarinCorePlugin plugin, ProfileService profiles) {
        this.plugin = plugin;
        this.profiles = profiles;
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            profiles.getCached(player.getUniqueId())
                    .ifPresent(profile -> profiles.save(profile).whenComplete((unused, error) -> {
                        if (error != null) {
                            plugin.getLogger().log(Level.WARNING,
                                    "Periodic profile save failed for " + profile.getName(), error);
                        }
                    }));
        }
    }
}
