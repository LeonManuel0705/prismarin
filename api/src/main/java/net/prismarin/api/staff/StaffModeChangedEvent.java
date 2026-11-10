package net.prismarin.api.staff;

import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record StaffModeChangedEvent(UUID player, boolean enabled, boolean nicked, boolean vanished)
        implements PrismarinEvent {
}
