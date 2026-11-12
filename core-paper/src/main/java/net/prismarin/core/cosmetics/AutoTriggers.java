package net.prismarin.core.cosmetics;

import net.prismarin.api.cosmetics.CosmeticEffectEvent;
import net.prismarin.api.cosmetics.CosmeticSlot;
import net.prismarin.shared.cosmetics.KillTrigger;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

final class AutoTriggers implements Listener {

    private final PaperCosmetics cosmetics;

    AutoTriggers(PaperCosmetics cosmetics) {
        this.cosmetics = cosmetics;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player shooter)
                || !cosmetics.automatic(CosmeticSlot.PROJECTILE_TRAIL)
                || !cosmetics.settings().triggers().tracks(projectile.getType().getKey().getKey())) {
            return;
        }
        cosmetics.startTrail(shooter, projectile, CosmeticEffectEvent.Trigger.PROJECTILE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        if (event.getHitBlock() != null) {
            cosmetics.landed(event.getEntity().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnload(EntitiesUnloadEvent event) {
        cosmetics.unloaded(event.getEntities());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof ArmorStand) {
            return;
        }
        Player killer = victim.getKiller();
        KillTrigger.Kill kill = new KillTrigger.Kill(killer != null,
                killer != null && killer.getUniqueId().equals(victim.getUniqueId()), victim instanceof Player,
                cosmetics.effectEntity(victim.getUniqueId()));
        KillTrigger.Verdict verdict = KillTrigger.judge(cosmetics.automatic(CosmeticSlot.KILL_EFFECT),
                cosmetics.settings().triggers().mobKills(), kill, cosmetics.active(CosmeticSlot.KILL_EFFECT),
                killer != null && cosmetics.visibleWearer(killer));
        if (verdict == KillTrigger.Verdict.PLAY) {
            cosmetics.playAt(killer, CosmeticSlot.KILL_EFFECT, victim.getLocation(),
                    CosmeticEffectEvent.Trigger.KILL);
        }
    }
}
