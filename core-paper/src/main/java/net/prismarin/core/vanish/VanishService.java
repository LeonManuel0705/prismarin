package net.prismarin.core.vanish;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.moderation.ModerationChannel;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

@Singleton
public final class VanishService {

    public static final String USE = "prismarin.vanish.use";
    public static final String OTHERS = "prismarin.vanish.others";
    public static final String SEE = "prismarin.vanish.see";
    public static final String SETTING = "vanished";
    private static final String LEVEL_PREFIX = "prismarin.vanish.level.";

    private final PrismarinCorePlugin plugin;
    private final ProfileService profiles;
    private final ModerationChannel channel;
    private final Map<UUID, Integer> vanished = new ConcurrentHashMap<>();

    @Inject
    public VanishService(PrismarinCorePlugin plugin, ProfileService profiles, ModerationChannel channel) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.channel = channel;
    }

    public boolean vanished(Player player) {
        return vanished.containsKey(player.getUniqueId());
    }

    public boolean canSee(Player viewer, Player target) {
        Integer level = vanished.get(target.getUniqueId());
        if (level == null || viewer.getUniqueId().equals(target.getUniqueId())) {
            return true;
        }
        return viewer.hasPermission(SEE) && level(viewer) >= level;
    }

    public int visibleCount(Player viewer) {
        int count = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (viewer == null ? !vanished(player) : canSee(viewer, player)) {
                count++;
            }
        }
        return count;
    }

    public void vanish(Player player) {
        int level = level(player);
        vanished.put(player.getUniqueId(), level);
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (!canSee(viewer, player)) {
                viewer.hidePlayer(plugin, player);
            }
        }
        player.setCollidable(false);
        player.setSilent(true);
        player.setSleepingIgnored(true);
        player.setAffectsSpawning(false);
        remember(player, true);
        channel.sendVanish(player, true, level);
    }

    public void reveal(Player player) {
        vanished.remove(player.getUniqueId());
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            viewer.showPlayer(plugin, player);
        }
        player.setCollidable(true);
        player.setSilent(false);
        player.setSleepingIgnored(false);
        player.setAffectsSpawning(true);
        remember(player, false);
        channel.sendVanish(player, false, 0);
    }

    public void hideVanishedFrom(Player viewer) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!canSee(viewer, player)) {
                viewer.hidePlayer(plugin, player);
            }
        }
    }

    public boolean remembered(Player player) {
        return profiles.getCached(player.getUniqueId())
                .map(profile -> Boolean.parseBoolean(profile.getSettings().get(SETTING)))
                .orElse(false);
    }

    void restoreSilently(Player player) {
        vanished.put(player.getUniqueId(), level(player));
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (!canSee(viewer, player)) {
                viewer.hidePlayer(plugin, player);
            }
        }
        player.setCollidable(false);
        player.setSilent(true);
        player.setSleepingIgnored(true);
        player.setAffectsSpawning(false);
    }

    void announce(Player player) {
        channel.sendVanish(player, true, level(player));
    }

    void forget(Player player) {
        vanished.remove(player.getUniqueId());
    }

    boolean any() {
        return !vanished.isEmpty();
    }

    static int level(Player player) {
        int level = 1;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String permission = info.getPermission();
            if (info.getValue() && permission.startsWith(LEVEL_PREFIX)) {
                try {
                    level = Math.max(level, Integer.parseInt(permission.substring(LEVEL_PREFIX.length())));
                } catch (NumberFormatException ignored) {
                    continue;
                }
            }
        }
        return level;
    }

    private void remember(Player player, boolean state) {
        profiles.modify(player.getUniqueId(), profile -> {
            if (state) {
                profile.getSettings().put(SETTING, "true");
            } else {
                profile.getSettings().remove(SETTING);
            }
        });
    }
}
