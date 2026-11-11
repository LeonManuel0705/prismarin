package net.prismarin.example;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.Prismarin;
import net.prismarin.api.admin.Delivery;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.admin.StaffAlert;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.PunishmentPreview;
import net.prismarin.api.moderation.ReportSubmission;

final class Moderation {

    static final String SOURCE = "Example";

    private final Prismarin prismarin;

    Moderation(Prismarin prismarin) {
        this.prismarin = prismarin;
    }

    CompletableFuture<ReportSubmission> report(UUID reporter, String reporterName, UUID target) {
        return prismarin.reports()
                .map(reports -> reports.file(reporter, reporterName, target, "hacking Example check"))
                .orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("reports are off")));
    }

    CompletableFuture<Result> punish(UUID target, String template, String actor) {
        return prismarin.punishmentTemplates().map(templates -> templates.preview(target, template)
                        .thenCompose(preview -> templates.punish(target, template, actor, "example plugin")
                                .thenApply(punishment -> new Result(preview, punishment))))
                .orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("punishments are off")));
    }

    CompletableFuture<Delivery> alert(String actor, String targetName, String text) {
        NoticeText notice = NoticeText.of("%player% was flagged: %reason%")
                .and("de", "%player% ist aufgefallen: %reason%")
                .with("player", targetName)
                .with("reason", text);
        return prismarin.audit().record(actor, "example.flag", targetName, text)
                .thenCompose(recorded -> prismarin.notifications().notifyStaff(StaffAlert.of(SOURCE, notice)));
    }

    record Result(PunishmentPreview preview, Punishment punishment) {
    }
}
