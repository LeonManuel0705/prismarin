package net.prismarin.api.stats;

import java.util.Objects;
import java.util.UUID;

public record StatChange(UUID player, String key, Type type, long previous, long value, long weekly, long monthly) {

    public enum Type {
        INCREMENT,
        SET,
        RECORD
    }

    public StatChange {
        Objects.requireNonNull(player, "player");
        key = StatKeys.key(key);
        Objects.requireNonNull(type, "type");
    }

    public boolean changed() {
        return previous != value;
    }
}
