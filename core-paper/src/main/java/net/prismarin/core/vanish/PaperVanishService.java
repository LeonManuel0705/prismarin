package net.prismarin.core.vanish;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.vanish.VanishChangedEvent;
import net.prismarin.core.PrismarinCorePlugin;
import org.bukkit.entity.Player;

@Singleton
public final class PaperVanishService implements net.prismarin.api.vanish.VanishService {

    private final PrismarinCorePlugin plugin;
    private final VanishService vanish;

    @Inject
    public PaperVanishService(PrismarinCorePlugin plugin, VanishService vanish) {
        this.plugin = plugin;
        this.vanish = vanish;
    }

    @Override
    public boolean vanished(UUID player) {
        return player != null && vanish.vanished(player);
    }

    @Override
    public Set<UUID> vanishedPlayers() {
        return vanish.vanishedIds();
    }

    @Override
    public int level(UUID player) {
        Integer vanishedLevel = vanish.vanishedLevel(player);
        if (vanishedLevel != null) {
            return vanishedLevel;
        }
        Player online = plugin.getServer().getPlayer(player);
        return online == null ? 0 : VanishService.level(online);
    }

    @Override
    public boolean canSee(UUID viewer, UUID target) {
        Integer targetLevel = vanish.vanishedLevel(target);
        if (targetLevel == null) {
            return true;
        }
        if (viewer == null) {
            return false;
        }
        if (viewer.equals(target)) {
            return true;
        }
        Player online = plugin.getServer().getPlayer(viewer);
        return online != null && VanishRules.canSee(false, targetLevel, online.hasPermission(VanishService.SEE),
                VanishService.level(online));
    }

    @Override
    public boolean vanish(UUID player) {
        Player online = online(player);
        return online != null && vanish.vanish(online, VanishChangedEvent.Cause.PLUGIN);
    }

    @Override
    public boolean reveal(UUID player) {
        Player online = online(player);
        return online != null && vanish.reveal(online, VanishChangedEvent.Cause.PLUGIN);
    }

    private Player online(UUID player) {
        if (!plugin.getServer().isPrimaryThread()) {
            throw new IllegalStateException("vanish and reveal must be called on the main thread");
        }
        return player == null ? null : plugin.getServer().getPlayer(player);
    }
}
