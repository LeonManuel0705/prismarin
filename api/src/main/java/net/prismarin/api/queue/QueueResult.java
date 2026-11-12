package net.prismarin.api.queue;

import java.util.Objects;
import java.util.Optional;

public record QueueResult(QueueStatus status, Optional<String> server, Optional<QueuePosition> position) {

    public QueueResult {
        Objects.requireNonNull(status, "status");
        server = server == null ? Optional.empty() : server;
        position = position == null ? Optional.empty() : position;
    }

    public static QueueResult of(QueueStatus status) {
        return new QueueResult(status, Optional.empty(), Optional.empty());
    }

    public static QueueResult sent(String server) {
        return new QueueResult(QueueStatus.SENT, Optional.ofNullable(server), Optional.empty());
    }

    public static QueueResult waiting(QueueStatus status, QueuePosition position) {
        return new QueueResult(status, Optional.empty(), Optional.ofNullable(position));
    }

    public boolean sent() {
        return status == QueueStatus.SENT;
    }

    public boolean waiting() {
        return status.waiting();
    }
}
