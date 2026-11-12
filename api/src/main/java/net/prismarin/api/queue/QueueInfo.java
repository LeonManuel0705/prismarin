package net.prismarin.api.queue;

import java.util.List;
import java.util.Objects;

public record QueueInfo(QueueTarget target, String displayName, int size, int players, boolean paused,
                        List<QueueEntry> entries) {

    public QueueInfo {
        Objects.requireNonNull(target, "target");
        displayName = displayName == null || displayName.isBlank() ? target.name() : displayName;
        entries = List.copyOf(entries);
    }

    public boolean empty() {
        return size == 0;
    }
}
