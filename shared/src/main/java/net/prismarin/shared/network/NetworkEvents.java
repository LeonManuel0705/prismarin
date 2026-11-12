package net.prismarin.shared.network;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.joinme.JoinMeCreatedEvent;
import net.prismarin.api.joinme.JoinMeExpiredEvent;
import net.prismarin.api.moderation.Report;
import net.prismarin.api.moderation.ReportFiledEvent;
import net.prismarin.api.reward.Vote;
import net.prismarin.api.reward.VoteReceivedEvent;
import net.prismarin.shared.queue.QueueEvents;
import net.prismarin.shared.joinme.JoinMeCodec;
import net.prismarin.shared.social.ClanEvents;

public final class NetworkEvents {

    public static final String REPORT_FILED = "report-filed";
    public static final String VOTE_RECEIVED = "vote-received";
    public static final String JOINME_CREATED = "joinme-created";
    public static final String JOINME_EXPIRED = "joinme-expired";

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
        if (event instanceof VoteReceivedEvent received) {
            JsonObject message = new JsonObject();
            message.addProperty("event", VOTE_RECEIVED);
            message.add("vote", ApiJson.tree(received.vote()));
            return NetworkCalls.fits(message) ? Optional.of(message) : Optional.empty();
        }
        Optional<JsonObject> queue = QueueEvents.encode(event);
        if (queue.isPresent()) {
            return queue.filter(NetworkCalls::fits);
        }
        if (event instanceof JoinMeCreatedEvent created) {
            JsonObject message = new JsonObject();
            message.addProperty("event", JOINME_CREATED);
            message.add("joinme", JoinMeCodec.encode(created.joinMe()));
            return NetworkCalls.fits(message) ? Optional.of(message) : Optional.empty();
        }
        if (event instanceof JoinMeExpiredEvent expired) {
            JsonObject message = new JsonObject();
            message.addProperty("event", JOINME_EXPIRED);
            message.add("joinme", JoinMeCodec.encode(expired.joinMe()));
            message.addProperty("reason", expired.reason().id());
            return NetworkCalls.fits(message) ? Optional.of(message) : Optional.empty();
        }
        return ClanEvents.encode(event).filter(NetworkCalls::fits);
    }

    public static Optional<PrismarinEvent> decode(JsonObject message) {
        if (message == null) {
            return Optional.empty();
        }
        try {
            String event = NetworkCalls.text(message.get("event"));
            if (REPORT_FILED.equals(event)) {
                return Optional.of(new ReportFiledEvent(ApiJson.read(message.get("report"), Report.class)));
            }
            if (NetworkCalls.text(message.get("event")).startsWith(ClanEvents.PREFIX)) {
                return ClanEvents.decode(message);
            }
            if (VOTE_RECEIVED.equals(event)) {
                return Optional.of(new VoteReceivedEvent(ApiJson.read(message.get("vote"), Vote.class)));
            }
            if (NetworkCalls.text(message.get("event")).startsWith(QueueEvents.PREFIX)) {
                return QueueEvents.decode(message);
            }
            if (JOINME_CREATED.equals(event)) {
                return JoinMeCodec.joinMe(message.get("joinme")).map(JoinMeCreatedEvent::new);
            }
            if (JOINME_EXPIRED.equals(event)) {
                return JoinMeCodec.joinMe(message.get("joinme")).map(joinMe -> new JoinMeExpiredEvent(joinMe,
                        JoinMeExpiredEvent.Reason.byId(NetworkCalls.text(message.get("reason")))
                                .orElse(JoinMeExpiredEvent.Reason.EXPIRED)));
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
