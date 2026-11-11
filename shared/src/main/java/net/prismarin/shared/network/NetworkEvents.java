package net.prismarin.shared.network;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.moderation.Report;
import net.prismarin.api.moderation.ReportFiledEvent;

public final class NetworkEvents {

    public static final String REPORT_FILED = "report-filed";

    private NetworkEvents() {
        throw new AssertionError();
    }

    public static Optional<JsonObject> encode(PrismarinEvent event) {
        if (event instanceof ReportFiledEvent filed) {
            JsonObject message = message(REPORT_FILED, filed.report());
            if (!NetworkCalls.fits(message)) {
                message = message(REPORT_FILED, withoutChat(filed.report()));
            }
            return NetworkCalls.fits(message) ? Optional.of(message) : Optional.empty();
        }
        return Optional.empty();
    }

    public static Optional<PrismarinEvent> decode(JsonObject message) {
        if (message == null) {
            return Optional.empty();
        }
        try {
            if (REPORT_FILED.equals(NetworkCalls.text(message.get("event")))) {
                return Optional.of(new ReportFiledEvent(ApiJson.read(message.get("report"), Report.class)));
            }
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static JsonObject message(String event, Report report) {
        JsonObject message = new JsonObject();
        message.addProperty("event", event);
        message.add("report", ApiJson.tree(report));
        return message;
    }

    private static Report withoutChat(Report report) {
        return new Report(report.id(), report.target(), report.targetName(), report.shownName(), report.server(),
                report.context(), report.reporters(), List.of(), report.createdAt(), report.holder(),
                report.claimedAt(), report.open(), report.closedBy(), report.closedAt(), report.verdict());
    }
}
