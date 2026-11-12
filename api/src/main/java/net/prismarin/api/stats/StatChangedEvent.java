package net.prismarin.api.stats;

import java.util.Objects;
import net.prismarin.api.event.PrismarinEvent;

public record StatChangedEvent(StatChange change) implements PrismarinEvent {

    public StatChangedEvent {
        Objects.requireNonNull(change, "change");
    }
}
