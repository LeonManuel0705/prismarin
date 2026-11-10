package net.prismarin.core.vanish;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.config.VanishSettings;
import net.prismarin.core.moderation.ModerationChannel;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

@Singleton
public final class VanishService {

    public static final String USE = "prismarin.vanish.use";
    public static final String OTHERS = "prismarin.vanish.others";
    public static final String SEE = "prismarin.vanish.see";
    public static final String SETTING = "vanished";
    private static final String LEVEL_PREFIX = "prismarin.vanish.level.";
    private static final long FALL_GRACE_MILLIS = 15_000L;

    private final PrismarinCorePlugin plugin;
    private final ProfileService profiles;
    private final ModerationChannel channel;
    private final VanishSettings settings;
    private final VanishMobs mobs;
    private final Map<UUID, Integer> vanished = new ConcurrentHashMap<>();
    private final Set<UUID> grantedFlight = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> fallGrace = new ConcurrentHashMap<>();

    @Inject
    public VanishService(PrismarinCorePlugin plugin, ProfileService profiles, ModerationChannel channel,
                         VanishSettings settings, VanishMobs mobs) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.channel = channel;
        this.settings = settings;
        this.mobs = mobs;
    }

    public VanishSettings settings() {
        return settings;
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
        applyPerks(player, false);
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
        dropPerks(player);
        if (vanished.isEmpty()) {
            mobs.release();
        }
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
        applyPerks(player, true);
    }

    void announce(Player player) {
        channel.sendVanish(player, true, level(player));
    }

    void forget(Player player) {
        if (vanished.remove(player.getUniqueId()) != null && grantedFlight.remove(player.getUniqueId())
                && survivalLike(player)) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        fallGrace.remove(player.getUniqueId());
        if (vanished.isEmpty()) {
            mobs.release();
        }
    }

    void calmMobs() {
        if (!settings.mobsIgnore()) {
            return;
        }
        if (vanished.isEmpty()) {
            mobs.release();
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (vanished(player)) {
                mobs.calm(player, this);
            }
        }
    }

    void grantFlight(Player player) {
        if (settings.flight() && vanished(player) && !player.getAllowFlight()) {
            player.setAllowFlight(true);
            grantedFlight.add(player.getUniqueId());
        }
    }

    public void releaseFlight(Player player) {
        grantedFlight.remove(player.getUniqueId());
    }

    boolean consumeFallGrace(Player player) {
        Long until = fallGrace.remove(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    private void applyPerks(Player player, boolean rejoin) {
        if (settings.flight() && !player.getAllowFlight()) {
            player.setAllowFlight(true);
            grantedFlight.add(player.getUniqueId());
            if (rejoin && player.getLocation().clone().subtract(0, 0.2, 0).getBlock().isPassable()) {
                player.setFlying(true);
            }
        }
        if (settings.invincible()) {
            player.setFireTicks(0);
        }
        if (settings.mobsIgnore()) {
            mobs.calm(player, this);
        }
    }

    private void dropPerks(Player player) {
        boolean granted = grantedFlight.remove(player.getUniqueId());
        if (!survivalLike(player)) {
            return;
        }
        if (player.isFlying() || player.getLocation().clone().subtract(0, 0.2, 0).getBlock().isPassable()) {
            fallGrace.put(player.getUniqueId(), System.currentTimeMillis() + FALL_GRACE_MILLIS);
        }
        if (settings.flight()) {
            player.setFlying(false);
        }
        if (granted) {
            player.setAllowFlight(false);
        }
        player.setFallDistance(0F);
    }

    private static boolean survivalLike(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
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
