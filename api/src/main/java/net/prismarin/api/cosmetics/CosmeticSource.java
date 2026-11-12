package net.prismarin.api.cosmetics;

import java.util.Locale;
import java.util.Optional;

public enum CosmeticSource {
    PURCHASE,
    PERMISSION,
    REWARD,
    STAFF,
    PLUGIN;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<CosmeticSource> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (CosmeticSource source : values()) {
            if (source.id().equalsIgnoreCase(id.strip())) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }
}
