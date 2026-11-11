package net.prismarin.api.admin;

import java.util.Objects;
import net.prismarin.api.event.PrismarinEvent;

public record StaffAlertEvent(StaffAlert alert, String server) implements PrismarinEvent {

    public StaffAlertEvent {
        Objects.requireNonNull(alert, "alert");
        server = server == null ? "" : server;
    }
}
