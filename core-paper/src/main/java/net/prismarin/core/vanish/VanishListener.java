package net.prismarin.core.vanish;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Locale;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.config.VanishSettings;
import net.prismarin.core.locale.PlayerLocales;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

@Singleton
public final class VanishListener implements Listener {

    private static final long REMINDER_TICKS = 40L;
    private static final long MOB_TICKS = 10L;

    private final PrismarinCorePlugin plugin;
    private final VanishService vanish;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final VanishSettings settings;
    private BukkitTask reminder;
    private BukkitTask mobs;

    @Inject
    public VanishListener(PrismarinCorePlugin plugin, VanishService vanish, LocaleService locale,
                          ProfileService profiles, VanishSettings settings) {
        this.plugin = plugin;
        this.vanish = vanish;
        this.locale = locale;
        this.profiles = profiles;
        this.settings = settings;
    }

    public void start() {
        reminder = plugin.getServer().getScheduler().runTaskTimer(plugin, this::remind, REMINDER_TICKS, REMINDER_TICKS);
        mobs = plugin.getServer().getScheduler().runTaskTimer(plugin, vanish::calmMobs, MOB_TICKS, MOB_TICKS);
    }

    public void stop() {
        if (reminder != null) {
            reminder.cancel();
            reminder = null;
        }
        if (mobs != null) {
            mobs.cancel();
            mobs = null;
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
        if (settings.mobsIgnore() && event.getTarget() instanceof Player player && vanish.vanished(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !vanish.vanished(player)) {
            if (event.getEntity() instanceof Player player && event.getCause() == EntityDamageEvent.DamageCause.FALL
                    && vanish.consumeFallGrace(player)) {
                event.setCancelled(true);
            }
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (settings.invincible() && cause != EntityDamageEvent.DamageCause.VOID
                && cause != EntityDamageEvent.DamageCause.KILL && cause != EntityDamageEvent.DamageCause.WORLD_BORDER) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (settings.invincible() && event.getEntity() instanceof Player player && vanish.vanished(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (settings.noHunger() && event.getEntity() instanceof Player player && vanish.vanished(player)
                && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (vanish.vanished(player)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    vanish.grantFlight(player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onOpenContainer(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!settings.silentContainers() || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || !vanish.vanished(player) || player.isSneaking()
                || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        if (!(event.getClickedBlock().getState() instanceof Container container)) {
            return;
        }
        Inventory source = container.getInventory();
        if (source.getSize() % 9 != 0) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        Locale target = PlayerLocales.of(player, profiles);
        SilentContainer silent = new SilentContainer(event.getClickedBlock().getLocation(), source,
                locale.render("vanish.silent-title", target));
        player.openInventory(silent.getInventory());
    }

    @EventHandler
    public void onCloseContainer(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof SilentContainer silent)) {
            return;
        }
        if (!(silent.location().getBlock().getState() instanceof Container container)) {
            return;
        }
        Inventory current = container.getInventory();
        Player player = (Player) event.getPlayer();
        if (!silent.unchanged(current)) {
            player.sendMessage(locale.render("vanish.container-changed", PlayerLocales.of(player, profiles)));
            return;
        }
        current.setContents(silent.getInventory().getContents());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!settings.silentBlocks() || !vanish.vanished(player)) {
            return;
        }
        event.setCancelled(true);
        Block block = event.getBlock();
        if (player.getGameMode() == GameMode.CREATIVE) {
            block.setType(Material.AIR);
        } else {
            block.breakNaturally(player.getInventory().getItemInMainHand(), false, true);
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
                player.sendActionBar(locale.render("vanish.actionbar", PlayerLocales.of(player, profiles)));
            }
        }
    }
}
