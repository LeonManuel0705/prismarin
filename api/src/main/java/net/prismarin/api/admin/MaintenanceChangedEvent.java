package net.prismarin.api.admin;

import java.util.Objects;
import net.prismarin.api.event.PrismarinEvent;

public record MaintenanceChangedEvent(MaintenanceState state, String actor) implements PrismarinEvent {

    public MaintenanceChangedEvent {
        Objects.requireNonNull(state, "state");
        actor = actor == null ? "" : actor;
    }
}
