package net.prismarin.core.vanish;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.locale.PlayerLocales;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

@Singleton
public final class VanishListener implements Listener {

    private static final long REMINDER_TICKS = 40L;

    private final PrismarinCorePlugin plugin;
    private final VanishService vanish;
    private final LocaleService locale;
    private final ProfileService profiles;
    private BukkitTask reminder;

    @Inject
    public VanishListener(PrismarinCorePlugin plugin, VanishService vanish, LocaleService locale,
                          ProfileService profiles) {
        this.plugin = plugin;
        this.vanish = vanish;
        this.locale = locale;
        this.profiles = profiles;
    }

    public void start() {
        reminder = plugin.getServer().getScheduler().runTaskTimer(plugin, this::remind, REMINDER_TICKS, REMINDER_TICKS);
    }

    public void stop() {
        if (reminder != null) {
            reminder.cancel();
            reminder = null;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        vanish.hideVanishedFrom(player);
        if (player.hasPermission(VanishService.USE) && vanish.remembered(player)) {
            vanish.restoreSilently(player);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && vanish.vanished(player)) {
                    vanish.announce(player);
                }
            }, 20L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        vanish.forget(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && vanish.vanished(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && vanish.vanished(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPhysical(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && vanish.vanished(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVibration(BlockReceiveGameEvent event) {
        if (event.getEntity() instanceof Player player && vanish.vanished(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (vanish.vanished(event.getPlayer())) {
            event.message(null);
        }
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        if (!vanish.any()) {
            return;
        }
        event.getListedPlayers().removeIf(listed -> {
            Player player = plugin.getServer().getPlayer(listed.id());
            return player != null && vanish.vanished(player);
        });
        event.setNumPlayers(vanish.visibleCount(null));
    }

    private void remind() {
        if (!vanish.any()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (vanish.vanished(player)) {
                player.sendActionBar(locale.render("vanish.reminder", PlayerLocales.of(player, profiles)));
            }
        }
    }
}
