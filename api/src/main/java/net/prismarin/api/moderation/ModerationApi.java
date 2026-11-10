package net.prismarin.api.moderation;

import java.util.Optional;

public final class ModerationApi {

    private static volatile ReportService reports;
    private static volatile PunishmentTemplates templates;

    private ModerationApi() {
        throw new AssertionError();
    }

    public static Optional<ReportService> reports() {
        return Optional.ofNullable(reports);
    }

    public static Optional<PunishmentTemplates> templates() {
        return Optional.ofNullable(templates);
    }

    public static void registerReports(ReportService service) {
        reports = service;
    }

    public static void registerTemplates(PunishmentTemplates service) {
        templates = service;
    }

    public static void unregister() {
        reports = null;
        templates = null;
    }
}
