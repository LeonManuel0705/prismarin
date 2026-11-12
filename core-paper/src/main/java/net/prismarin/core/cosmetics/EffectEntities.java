package net.prismarin.core.cosmetics;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.prismarin.shared.cosmetics.EffectEntityRegistry;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

final class EffectEntities implements Listener {

    static final String KEY = "cosmetic-effect";

    private final Plugin plugin;
    private final NamespacedKey key;
    private final EffectEntityRegistry registry;
    private long lightningRun = -1L;

    EffectEntities(Plugin plugin, int max) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, KEY);
        this.registry = new EffectEntityRegistry(max);
    }

    void max(int value) {
        registry.max(value);
    }

    boolean room(int count) {
        return registry.room(count);
    }

    boolean marked(UUID entity) {
        return registry.marked(entity);
    }

    boolean marked(Entity entity) {
        return entity != null && (registry.marked(entity.getUniqueId())
                || entity.getPersistentDataContainer().has(key, PersistentDataType.LONG));
    }

    int size() {
        return registry.size();
    }

    <T extends Entity> T spawn(World world, Location at, Class<T> type, long run, List<Player> viewers,
                               Consumer<? super T> setup) {
        if (!registry.room(1)) {
            return null;
        }
        T entity = world.spawn(at, type, spawned -> {
            prepare(spawned, run);
            setup.accept(spawned);
        });
        return added(entity, viewers);
    }

    Item drop(World world, Location at, ItemStack stack, long run, List<Player> viewers, Consumer<Item> setup) {
        if (!registry.room(1)) {
            return null;
        }
        Item item = world.dropItem(at, stack, dropped -> {
            prepare(dropped, run);
            dropped.setCanPlayerPickup(false);
            dropped.setCanMobPickup(false);
            dropped.setPickupDelay(Short.MAX_VALUE);
            dropped.setWillAge(false);
            setup.accept(dropped);
        });
        return added(item, viewers);
    }

    LightningStrike lightning(World world, Location at, long run, List<Player> viewers) {
        if (!registry.room(1) || !at.getBlock().isEmpty()) {
            return null;
        }
        lightningRun = run;
        LightningStrike strike;
        try {
            strike = world.strikeLightningEffect(at);
        } finally {
            lightningRun = -1L;
        }
        if (strike == null || !registry.marked(strike.getUniqueId())) {
            return null;
        }
        return added(strike, viewers);
    }

    void adopt(Entity entity, long run, List<Player> viewers) {
        entity.getPersistentDataContainer().set(key, PersistentDataType.LONG, run);
        registry.adopt(entity.getUniqueId(), run);
        entity.setVisibleByDefault(false);
        viewers.forEach(viewer -> viewer.showEntity(plugin, entity));
    }

    void remove(Entity entity, List<Player> viewers) {
        registry.release(entity.getUniqueId());
        try {
            entity.remove();
        } finally {
            if (plugin.isEnabled()) {
                for (Player viewer : viewers) {
                    if (viewer.isOnline()) {
                        viewer.hideEntity(plugin, entity);
                    }
                }
            }
        }
    }

    void clear() {
        registry.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLightning(LightningStrikeEvent event) {
        LightningStrike strike = event.getLightning();
        if (lightningRun < 0L || !strike.isEffect()) {
            return;
        }
        strike.setVisibleByDefault(false);
        strike.getPersistentDataContainer().set(key, PersistentDataType.LONG, lightningRun);
        registry.mark(strike.getUniqueId(), lightningRun);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(EntitySpawnEvent event) {
        if (event.isCancelled() && marked(event.getEntity())) {
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLightningAllowed(LightningStrikeEvent event) {
        if (event.isCancelled() && lightningRun >= 0L && marked(event.getLightning())) {
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean ending = cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.KILL;
        if (!ending && marked(event.getEntity())
                || event instanceof EntityDamageByEntityEvent byEntity && marked(byEntity.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageAgain(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity && marked(byEntity.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombust(EntityCombustEvent event) {
        if (marked(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTarget(EntityTargetEvent event) {
        if (marked(event.getEntity()) || marked(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        if (marked(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPhysical(EntityInteractEvent event) {
        if (marked(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameEvent(BlockReceiveGameEvent event) {
        if (marked(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (marked(event.getItem()) || marked(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHopper(InventoryPickupItemEvent event) {
        if (marked(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMerge(ItemMergeEvent event) {
        if (marked(event.getEntity()) || marked(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(EntityDropItemEvent event) {
        if (marked(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (marked(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (marked(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onShear(PlayerShearEntityEvent event) {
        if (marked(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (marked(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVehicle(VehicleEnterEvent event) {
        if (marked(event.getEntered())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPortal(EntityPortalEvent event) {
        if (marked(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (marked(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (marked(entity)) {
                registry.release(entity.getUniqueId());
                entity.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity.getPersistentDataContainer().has(key, PersistentDataType.LONG)) {
                registry.release(entity.getUniqueId());
                entity.remove();
            }
        }
    }

    private void prepare(Entity entity, long run) {
        entity.setVisibleByDefault(false);
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.getPersistentDataContainer().set(key, PersistentDataType.LONG, run);
        registry.mark(entity.getUniqueId(), run);
        if (entity instanceof LivingEntity living) {
            living.setCollidable(false);
            living.setCanPickupItems(false);
            living.setRemoveWhenFarAway(true);
        }
        if (entity instanceof Mob mob) {
            mob.setAware(false);
        }
    }

    private <T extends Entity> T added(T entity, List<Player> viewers) {
        if (entity == null) {
            return null;
        }
        if (!entity.isValid()) {
            registry.release(entity.getUniqueId());
            return null;
        }
        for (Player viewer : viewers) {
            viewer.showEntity(plugin, entity);
        }
        return entity;
    }
}
