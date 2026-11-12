package net.prismarin.api.moderation;

import java.util.Optional;

public final class ModerationApi {

    private static volatile ReportService reports;
    private static volatile PunishmentTemplates templates;
    private static volatile ChatFilterService chatFilter;
    private static volatile AltService alts;

    private ModerationApi() {
        throw new AssertionError();
    }

    public static Optional<ReportService> reports() {
        return Optional.ofNullable(reports);
    }

    public static Optional<PunishmentTemplates> templates() {
        return Optional.ofNullable(templates);
    }

    public static Optional<ChatFilterService> chatFilter() {
        return Optional.ofNullable(chatFilter);
    }

    public static Optional<AltService> alts() {
        return Optional.ofNullable(alts);
    }

    public static void registerReports(ReportService service) {
        reports = service;
    }

    public static void registerTemplates(PunishmentTemplates service) {
        templates = service;
    }

    public static void registerChatFilter(ChatFilterService service) {
        chatFilter = service;
    }

    public static void registerAlts(AltService service) {
        alts = service;
    }

    public static void unregister() {
        reports = null;
        templates = null;
        chatFilter = null;
        alts = null;
    }
}
