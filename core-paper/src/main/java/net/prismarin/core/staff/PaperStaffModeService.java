package net.prismarin.core.staff;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.UUID;
import net.prismarin.core.PrismarinCorePlugin;
import org.bukkit.entity.Player;

@Singleton
public final class PaperStaffModeService implements net.prismarin.api.staff.StaffModeService {

    private final PrismarinCorePlugin plugin;
    private final StaffModeService staff;

    @Inject
    public PaperStaffModeService(PrismarinCorePlugin plugin, StaffModeService staff) {
        this.plugin = plugin;
        this.staff = staff;
    }

    @Override
    public boolean active(UUID player) {
        Player online = player == null ? null : plugin.getServer().getPlayer(player);
        return online != null && staff.active(online);
    }

    @Override
    public boolean enter(UUID player) {
        Player online = online(player);
        return online != null && online.hasPermission(PERMISSION) && staff.enter(online);
    }

    @Override
    public boolean leave(UUID player) {
        Player online = online(player);
        return online != null && staff.leave(online);
    }

    @Override
    public boolean nickIncluded() {
        return staff.nickEnabled();
    }

    @Override
    public boolean vanishIncluded() {
        return staff.vanishEnabled();
    }

    private Player online(UUID player) {
        if (!plugin.getServer().isPrimaryThread()) {
            throw new IllegalStateException("staff mode must be switched on the main thread");
        }
        return player == null ? null : plugin.getServer().getPlayer(player);
    }
}
