package net.prismarin.core.vanish;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

final class VanishLookGoal implements Goal<Mob> {

    static final double RANGE = 12.0;
    private static final double RANGE_SQUARED = RANGE * RANGE;
    private static final int MIN_FOCUS_TICKS = 30;
    private static final int MAX_FOCUS_TICKS = 90;

    private final GoalKey<Mob> key;
    private final Mob mob;
    private final VanishService vanish;
    private Location focus;
    private int focusTicks;

    VanishLookGoal(GoalKey<Mob> key, Mob mob, VanishService vanish) {
        this.key = key;
        this.mob = mob;
        this.vanish = vanish;
    }

    @Override
    public boolean shouldActivate() {
        return vanish.any() && mob.getTarget() == null && nearestIsVanished();
    }

    @Override
    public boolean shouldStayActive() {
        return shouldActivate();
    }

    @Override
    public void start() {
        pickFocus();
    }

    @Override
    public void stop() {
        focus = null;
    }

    @Override
    public void tick() {
        if (focus == null || --focusTicks <= 0) {
            pickFocus();
        }
        mob.lookAt(focus);
    }

    @Override
    public GoalKey<Mob> getKey() {
        return key;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.LOOK);
    }

    private boolean nearestIsVanished() {
        Location location = mob.getLocation();
        Player nearest = null;
        double best = RANGE_SQUARED;
        for (Player player : mob.getWorld().getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(location);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest != null && vanish.vanished(nearest);
    }

    private void pickFocus() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(Math.PI * 2);
        Vector direction = new Vector(Math.cos(angle), random.nextDouble(-0.3, 0.3), Math.sin(angle)).multiply(6);
        focus = mob.getEyeLocation().add(direction);
        focusTicks = random.nextInt(MIN_FOCUS_TICKS, MAX_FOCUS_TICKS);
    }
}
