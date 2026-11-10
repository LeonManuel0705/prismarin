package net.prismarin.core.vanish;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.core.locale.PlayerLocales;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

@Singleton
public final class InventoryInspector implements Listener {

    public static final String PERMISSION = "prismarin.vanish.inspect";
    private static final long REFRESH_TICKS = 5L;

    private final PrismarinCorePlugin plugin;
    private final VanishService vanish;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final Map<UUID, BukkitTask> watchers = new ConcurrentHashMap<>();

    @Inject
    public InventoryInspector(PrismarinCorePlugin plugin, VanishService vanish, LocaleService locale,
                              ProfileService profiles) {
        this.plugin = plugin;
        this.vanish = vanish;
        this.locale = locale;
        this.profiles = profiles;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEntityEvent event) {
        Player viewer = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player target)
                || !vanish.settings().inspectInventories() || !vanish.vanished(viewer)
                || !viewer.hasPermission(PERMISSION) || target.hasMetadata("NPC")) {
            return;
        }
        event.setCancelled(true);
        open(viewer, target);
    }

    public void open(Player viewer, Player target) {
        Locale viewerLocale = PlayerLocales.of(viewer, profiles);
        LiveInventoryView view = new LiveInventoryView(target.getUniqueId(), locale.render("vanish.inspect.title",
                viewerLocale, Placeholder.unparsed("player", target.getName())));
        view.refresh(target, info(viewerLocale, target));
        viewer.openInventory(view.getInventory());
        stop(viewer.getUniqueId());
        watchers.put(viewer.getUniqueId(), plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Player owner = plugin.getServer().getPlayer(view.target());
            if (owner == null || !viewer.isOnline()
                    || !(viewer.getOpenInventory().getTopInventory().getHolder(false) instanceof LiveInventoryView open)
                    || open != view) {
                stop(viewer.getUniqueId());
                return;
            }
            view.refresh(owner, info(PlayerLocales.of(viewer, profiles), owner));
        }, REFRESH_TICKS, REFRESH_TICKS));
    }

    private ItemStack info(Locale viewerLocale, Player target) {
        double maxHealth = target.getAttribute(Attribute.MAX_HEALTH) == null ? 20D
                : target.getAttribute(Attribute.MAX_HEALTH).getValue();
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .skullOwner(target)
                .name(locale.render("vanish.inspect.info-name", viewerLocale,
                        Placeholder.unparsed("player", target.getName())))
                .lore(locale.render("vanish.inspect.info-health", viewerLocale,
                                Placeholder.unparsed("health", String.format(Locale.ROOT, "%.1f", target.getHealth())),
                                Placeholder.unparsed("max", String.format(Locale.ROOT, "%.0f", maxHealth))),
                        locale.render("vanish.inspect.info-food", viewerLocale,
                                Placeholder.unparsed("food", String.valueOf(target.getFoodLevel()))),
                        locale.render("vanish.inspect.info-level", viewerLocale,
                                Placeholder.unparsed("level", String.valueOf(target.getLevel()))),
                        locale.render("vanish.inspect.info-mode", viewerLocale,
                                Placeholder.unparsed("mode", target.getGameMode().name().toLowerCase(Locale.ROOT))),
                        locale.render("vanish.inspect.info-world", viewerLocale,
                                Placeholder.unparsed("world", target.getWorld().getName())))
                .build();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof LiveInventoryView) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof LiveInventoryView) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof LiveInventoryView) {
            stop(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer().getUniqueId());
    }

    private void stop(UUID viewer) {
        BukkitTask task = watchers.remove(viewer);
        if (task != null) {
            task.cancel();
        }
    }
}
