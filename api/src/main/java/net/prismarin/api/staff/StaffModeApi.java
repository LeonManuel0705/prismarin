package net.prismarin.api.staff;

import java.util.Optional;

public final class StaffModeApi {

    private static volatile StaffModeService staffMode;

    private StaffModeApi() {
        throw new AssertionError();
    }

    public static Optional<StaffModeService> staffMode() {
        return Optional.ofNullable(staffMode);
    }

    public static void register(StaffModeService service) {
        staffMode = service;
    }

    public static void unregister() {
        staffMode = null;
    }
}
