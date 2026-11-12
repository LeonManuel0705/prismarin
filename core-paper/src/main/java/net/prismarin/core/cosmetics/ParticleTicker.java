package net.prismarin.core.cosmetics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.prismarin.api.cosmetics.Cosmetic;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

final class ParticleTicker {

    static final int DEFERRED = -1;
    private static final double MOVE_SQUARED = 0.0004;

    interface Painter {

        int paint(Player wearer, Cosmetic cosmetic, long tick, boolean moving, int budget);

        int preview(Player viewer, Cosmetic cosmetic, Location anchor, long tick);
    }

    private record Worn(Cosmetic trail, Cosmetic aura) {
    }

    private static final class Preview {

        private final UUID viewer;
        private final Cosmetic cosmetic;
        private final Location anchor;
        private final long until;
        private long next;

        private Preview(UUID viewer, Cosmetic cosmetic, Location anchor, long until) {
            this.viewer = viewer;
            this.cosmetic = cosmetic;
            this.anchor = anchor;
            this.until = until;
        }
    }

    private final Plugin plugin;
    private final Painter painter;
    private final Map<UUID, Worn> worn = new LinkedHashMap<>();
    private final Map<UUID, double[]> positions = new HashMap<>();
    private final List<Preview> previews = new ArrayList<>();
    private BukkitTask task;
    private int interval;
    private int budget;
    private long tick;
    private int offset;

    ParticleTicker(Plugin plugin, Painter painter, int interval, int budget) {
        this.plugin = plugin;
        this.painter = painter;
        this.interval = Math.max(1, interval);
        this.budget = Math.max(1, budget);
    }

    void configure(int newInterval, int newBudget) {
        budget = Math.max(1, newBudget);
        int wanted = Math.max(1, newInterval);
        if (wanted != interval) {
            interval = wanted;
            if (task != null) {
                task.cancel();
                task = null;
                schedule();
            }
        }
    }

    void wear(UUID player, Cosmetic trail, Cosmetic aura) {
        if (trail == null && aura == null) {
            forget(player);
            return;
        }
        worn.put(player, new Worn(trail, aura));
        schedule();
    }

    void forget(UUID player) {
        worn.remove(player);
        positions.remove(player);
        previews.removeIf(preview -> preview.viewer.equals(player));
        stopWhenIdle();
    }

    void preview(UUID viewer, Cosmetic cosmetic, Location anchor, int seconds) {
        previews.removeIf(preview -> preview.viewer.equals(viewer));
        previews.add(new Preview(viewer, cosmetic, anchor.clone(), System.currentTimeMillis() + seconds * 1000L));
        schedule();
    }

    boolean running() {
        return task != null;
    }

    int wearers() {
        return worn.size();
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        worn.clear();
        positions.clear();
        previews.clear();
    }

    private void schedule() {
        if (task == null && plugin.isEnabled() && (!worn.isEmpty() || !previews.isEmpty())) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::run, 1L, interval);
        }
    }

    private void stopWhenIdle() {
        if (task != null && worn.isEmpty() && previews.isEmpty()) {
            task.cancel();
            task = null;
        }
    }

    private void run() {
        tick += interval;
        paintWearers();
        paintPreviews();
        stopWhenIdle();
    }

    private void paintWearers() {
        List<UUID> order = new ArrayList<>(worn.keySet());
        int size = order.size();
        if (size == 0) {
            return;
        }
        int start = Math.floorMod(offset, size);
        int remaining = budget;
        boolean painted = false;
        int stoppedAt = -1;
        for (int step = 0; step < size; step++) {
            UUID id = order.get((start + step) % size);
            Player player = plugin.getServer().getPlayer(id);
            if (player == null) {
                worn.remove(id);
                positions.remove(id);
                continue;
            }
            Worn entry = worn.get(id);
            boolean moving = moved(id, player.getLocation());
            Cosmetic cosmetic = moving && entry.trail() != null ? entry.trail() : entry.aura();
            if (cosmetic == null) {
                continue;
            }
            int used = painter.paint(player, cosmetic, tick, moving, painted ? remaining : Integer.MAX_VALUE);
            if (used == DEFERRED) {
                stoppedAt = (start + step) % size;
                break;
            }
            if (used > 0) {
                painted = true;
                remaining = Math.max(0, remaining - used);
            }
        }
        offset = stoppedAt >= 0 ? stoppedAt : start + 1;
    }

    private void paintPreviews() {
        long now = System.currentTimeMillis();
        Iterator<Preview> iterator = previews.iterator();
        while (iterator.hasNext()) {
            Preview preview = iterator.next();
            Player viewer = plugin.getServer().getPlayer(preview.viewer);
            if (viewer == null || now > preview.until || !viewer.getWorld().equals(preview.anchor.getWorld())) {
                iterator.remove();
                continue;
            }
            if (tick < preview.next) {
                continue;
            }
            preview.next = tick + Math.max(0, painter.preview(viewer, preview.cosmetic, preview.anchor, tick));
        }
    }

    private boolean moved(UUID id, Location location) {
        double[] now = {location.getX(), location.getY(), location.getZ()};
        double[] before = positions.put(id, now);
        if (before == null) {
            return false;
        }
        double dx = now[0] - before[0];
        double dy = now[1] - before[1];
        double dz = now[2] - before[2];
        return dx * dx + dy * dy + dz * dz > MOVE_SQUARED;
    }
}
