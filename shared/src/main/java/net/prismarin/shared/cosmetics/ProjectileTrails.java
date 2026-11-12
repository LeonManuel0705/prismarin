package net.prismarin.shared.cosmetics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.prismarin.api.cosmetics.Cosmetic;

public final class ProjectileTrails<P> {

    public interface Driver<P> {

        boolean flying(P projectile);

        boolean paint(P projectile, UUID owner, Cosmetic cosmetic, long age);
    }

    private static final class Flight<P> {

        private final P projectile;
        private final UUID owner;
        private final Cosmetic cosmetic;
        private long age;

        private Flight(P projectile, UUID owner, Cosmetic cosmetic) {
            this.projectile = projectile;
            this.owner = owner;
            this.cosmetic = cosmetic;
        }
    }

    private final TickClock clock;
    private final Driver<P> driver;
    private final Map<UUID, Flight<P>> flights = new LinkedHashMap<>();
    private int interval;
    private int maxTicks;
    private int maxTracked;
    private Runnable stopper;
    private int offset;

    public ProjectileTrails(TickClock clock, Driver<P> driver, int interval, int maxTicks, int maxTracked) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.driver = Objects.requireNonNull(driver, "driver");
        configure(interval, maxTicks, maxTracked);
    }

    public void configure(int newInterval, int newMaxTicks, int newMaxTracked) {
        interval = Math.max(1, newInterval);
        maxTicks = Math.max(1, newMaxTicks);
        maxTracked = Math.max(1, newMaxTracked);
    }

    public boolean track(UUID id, P projectile, UUID owner, Cosmetic cosmetic) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(projectile, "projectile");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(cosmetic, "cosmetic");
        if (flights.containsKey(id) || flights.size() >= maxTracked) {
            return false;
        }
        flights.put(id, new Flight<>(projectile, owner, cosmetic));
        if (stopper == null) {
            stopper = clock.start(this::tick);
        }
        return true;
    }

    public boolean tracking(UUID id) {
        return id != null && flights.containsKey(id);
    }

    public void land(UUID id) {
        if (id != null && flights.remove(id) != null) {
            stopWhenIdle();
        }
    }

    public int forgetOwner(UUID owner) {
        return forgetIf(flight -> flight.owner.equals(owner));
    }

    public int forget(Predicate<P> where) {
        return forgetIf(flight -> where.test(flight.projectile));
    }

    public int tracked() {
        return flights.size();
    }

    public boolean ticking() {
        return stopper != null;
    }

    public void stop() {
        flights.clear();
        stopWhenIdle();
    }

    public void tick() {
        List<Map.Entry<UUID, Flight<P>>> order = new ArrayList<>(flights.entrySet());
        int size = order.size();
        if (size > 0) {
            int start = Math.floorMod(offset, size);
            int refused = -1;
            for (int step = 0; step < size; step++) {
                Map.Entry<UUID, Flight<P>> entry = order.get((start + step) % size);
                Flight<P> flight = entry.getValue();
                if (flights.get(entry.getKey()) != flight) {
                    continue;
                }
                if (flight.age >= maxTicks || !driver.flying(flight.projectile)) {
                    flights.remove(entry.getKey());
                    continue;
                }
                if (flight.age % interval == 0 && !driver.paint(flight.projectile, flight.owner, flight.cosmetic,
                        flight.age) && refused < 0) {
                    refused = (start + step) % size;
                }
                flight.age++;
            }
            offset = refused >= 0 ? refused : start + 1;
        }
        stopWhenIdle();
    }

    private int forgetIf(Predicate<Flight<P>> which) {
        int before = flights.size();
        flights.values().removeIf(which);
        int removed = before - flights.size();
        if (removed > 0) {
            stopWhenIdle();
        }
        return removed;
    }

    private void stopWhenIdle() {
        if (flights.isEmpty() && stopper != null) {
            Runnable current = stopper;
            stopper = null;
            current.run();
        }
    }
}
