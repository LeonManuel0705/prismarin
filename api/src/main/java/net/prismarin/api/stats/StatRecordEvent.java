package net.prismarin.api.stats;

import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record StatRecordEvent(UUID player, String key, long previous, long value) implements PrismarinEvent {

    public StatRecordEvent {
        Objects.requireNonNull(player, "player");
        key = StatKeys.key(key);
    }
}
