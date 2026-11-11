package net.prismarin.api.vanish;

import java.util.Set;
import java.util.UUID;

public interface VanishService {

    String PERMISSION_USE = "prismarin.vanish.use";
    String PERMISSION_SEE = "prismarin.vanish.see";
    String LEVEL_PERMISSION_PREFIX = "prismarin.vanish.level.";

    boolean vanished(UUID player);

    Set<UUID> vanishedPlayers();

    int level(UUID player);

    boolean canSee(UUID viewer, UUID target);

    boolean vanish(UUID player);

    boolean reveal(UUID player);

    default boolean toggle(UUID player) {
        if (vanished(player)) {
            reveal(player);
        } else {
            vanish(player);
        }
        return vanished(player);
    }
}
