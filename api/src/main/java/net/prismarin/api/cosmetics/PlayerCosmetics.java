package net.prismarin.api.cosmetics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PlayerCosmetics(UUID player, Map<String, OwnedCosmetic> owned, Map<CosmeticSlot, String> equipped,
                              boolean hidesOthers) {

    public PlayerCosmetics {
        Objects.requireNonNull(player, "player");
        owned = owned == null || owned.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(owned));
        equipped = equipped == null || equipped.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(equipped));
    }

    public static PlayerCosmetics none(UUID player) {
        return new PlayerCosmetics(player, Map.of(), Map.of(), false);
    }

    public boolean owns(String id) {
        return id != null && owned.containsKey(id);
    }

    public Optional<String> equipped(CosmeticSlot slot) {
        return Optional.ofNullable(equipped.get(slot));
    }

    public boolean wears(String id) {
        return id != null && equipped.containsValue(id);
    }
}
