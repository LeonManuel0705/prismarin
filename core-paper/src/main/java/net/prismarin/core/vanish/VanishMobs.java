package net.prismarin.core.vanish;

import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.prismarin.core.PrismarinCorePlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.entity.memory.MemoryKey;

@Singleton
public final class VanishMobs {

    private static final double SCAN_RADIUS = 32.0;

    private final PrismarinCorePlugin plugin;
    private final GoalKey<Mob> key;
    private final Set<UUID> decorated = ConcurrentHashMap.newKeySet();

    @Inject
    public VanishMobs(PrismarinCorePlugin plugin) {
        this.plugin = plugin;
        this.key = GoalKey.of(Mob.class, new NamespacedKey(plugin, "vanish_look"));
    }

    public void calm(Player player, VanishService vanish) {
        MobGoals goals = plugin.getServer().getMobGoals();
        for (Mob mob : player.getLocation().getNearbyEntitiesByType(Mob.class, SCAN_RADIUS)) {
            forget(mob, player);
            if (!goals.hasGoal(mob, key)) {
                goals.addGoal(mob, 0, new VanishLookGoal(key, mob, vanish));
                decorated.add(mob.getUniqueId());
            }
        }
    }

    public void release() {
        if (decorated.isEmpty()) {
            return;
        }
        MobGoals goals = plugin.getServer().getMobGoals();
        for (UUID id : Set.copyOf(decorated)) {
            Entity entity = plugin.getServer().getEntity(id);
            if (entity instanceof Mob mob && goals.hasGoal(mob, key)) {
                goals.removeGoal(mob, key);
            }
        }
        decorated.clear();
    }

    private static void forget(Mob mob, Player player) {
        if (player.equals(mob.getTarget())) {
            mob.setTarget(null);
        }
        if (player.getUniqueId().equals(mob.getMemory(MemoryKey.ANGRY_AT))) {
            mob.setMemory(MemoryKey.ANGRY_AT, null);
        }
        if (mob instanceof Warden warden) {
            warden.clearAnger(player);
        }
    }
}
