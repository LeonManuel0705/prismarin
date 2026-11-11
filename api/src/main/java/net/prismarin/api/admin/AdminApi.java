package net.prismarin.api.admin;

import java.util.Optional;

public final class AdminApi {

    private static volatile AuditService audit;
    private static volatile MaintenanceService maintenance;
    private static volatile NotificationService notifications;

    private AdminApi() {
        throw new AssertionError();
    }

    public static Optional<AuditService> audit() {
        return Optional.ofNullable(audit);
    }

    public static Optional<MaintenanceService> maintenance() {
        return Optional.ofNullable(maintenance);
    }

    public static Optional<NotificationService> notifications() {
        return Optional.ofNullable(notifications);
    }

    public static IllegalStateException missing() {
        return new IllegalStateException("The PRISMARIN admin API is not available. Declare PrismarinCore as a "
                + "dependency of your plugin.");
    }

    public static void register(AuditService auditService, MaintenanceService maintenanceService,
                                NotificationService notificationService) {
        audit = auditService;
        maintenance = maintenanceService;
        notifications = notificationService;
    }

    public static void unregister() {
        audit = null;
        maintenance = null;
        notifications = null;
    }
}
