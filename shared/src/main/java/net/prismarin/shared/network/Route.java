package net.prismarin.shared.network;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public record Route(UUID carrier, boolean bus) {

    public static final Route BUS = new Route(null, true);
    public static final Route NONE = new Route(null, false);

    public static Route choose(Collection<UUID> online, UUID avoid, boolean busAvailable) {
        for (UUID player : online) {
            if (!player.equals(avoid)) {
                return new Route(player, false);
            }
        }
        if (busAvailable) {
            return BUS;
        }
        if (avoid != null && online.contains(avoid)) {
            return new Route(avoid, false);
        }
        return NONE;
    }

    public static Route prefer(Collection<UUID> online, UUID carrier, UUID avoid, boolean busAvailable) {
        if (carrier != null && !carrier.equals(avoid) && online.contains(carrier)) {
            return new Route(carrier, false);
        }
        return choose(online, avoid, busAvailable);
    }

    public Optional<UUID> player() {
        return Optional.ofNullable(carrier);
    }

    public boolean reachable() {
        return carrier != null || bus;
    }
}
