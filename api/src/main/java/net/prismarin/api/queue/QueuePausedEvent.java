package net.prismarin.api.queue;

import java.util.Objects;
import net.prismarin.api.event.PrismarinEvent;

public record QueuePausedEvent(QueueTarget target, boolean paused, String actor) implements PrismarinEvent {

    public QueuePausedEvent {
        Objects.requireNonNull(target, "target");
        actor = actor == null ? "" : actor;
    }
}
