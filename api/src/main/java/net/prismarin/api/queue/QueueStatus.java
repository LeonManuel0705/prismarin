package net.prismarin.api.queue;

import java.util.Locale;
import java.util.Optional;

public enum QueueStatus {
    SENT,
    QUEUED,
    ALREADY_QUEUED,
    ALREADY_THERE,
    NOT_LEADER,
    UNKNOWN_TARGET,
    NO_SERVER,
    OFFLINE,
    CANCELLED,
    DISABLED,
    FAILED;

    public boolean waiting() {
        return this == QUEUED || this == ALREADY_QUEUED;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<QueueStatus> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (QueueStatus status : values()) {
            if (status.id().equals(id)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
