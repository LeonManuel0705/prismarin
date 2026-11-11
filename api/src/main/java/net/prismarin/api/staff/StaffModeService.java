package net.prismarin.api.staff;

import java.util.UUID;

public interface StaffModeService {

    String PERMISSION = "prismarin.staff.mode";

    boolean active(UUID player);

    boolean enter(UUID player);

    boolean leave(UUID player);

    default boolean toggle(UUID player) {
        if (active(player)) {
            leave(player);
        } else {
            enter(player);
        }
        return active(player);
    }

    boolean nickIncluded();

    boolean vanishIncluded();
}
