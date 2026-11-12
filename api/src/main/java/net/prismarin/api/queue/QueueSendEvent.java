package net.prismarin.api.queue;

import java.util.List;
import java.util.UUID;
import net.prismarin.api.event.Cancellable;
import net.prismarin.api.event.PrismarinEvent;

public final class QueueSendEvent implements PrismarinEvent, Cancellable {

    private final QueueTarget target;
    private final String server;
    private final UUID player;
    private final List<UUID> party;
    private volatile boolean cancelled;

    public QueueSendEvent(QueueTarget target, String server, UUID player, List<UUID> party) {
        this.target = target;
        this.server = server;
        this.player = player;
        this.party = List.copyOf(party);
    }

    public QueueTarget target() {
        return target;
    }

    public String server() {
        return server;
    }

    public UUID player() {
        return player;
    }

    public List<UUID> party() {
        return party;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
