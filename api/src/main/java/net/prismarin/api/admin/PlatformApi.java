package net.prismarin.api.admin;

import java.util.Optional;

public final class PlatformApi {

    private static volatile ReloadService reloads;
    private static volatile UpdateService updates;

    private PlatformApi() {
        throw new AssertionError();
    }

    public static Optional<ReloadService> reloads() {
        return Optional.ofNullable(reloads);
    }

    public static Optional<UpdateService> updates() {
        return Optional.ofNullable(updates);
    }

    public static IllegalStateException missing() {
        return new IllegalStateException("The PRISMARIN platform API is not available. Declare PrismarinCore as a "
                + "dependency of your plugin.");
    }

    public static void register(ReloadService reloadService, UpdateService updateService) {
        reloads = reloadService;
        updates = updateService;
    }

    public static void unregister() {
        reloads = null;
        updates = null;
    }
}
