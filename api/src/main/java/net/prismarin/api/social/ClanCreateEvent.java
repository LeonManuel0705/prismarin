package net.prismarin.api.social;

import java.util.UUID;
import net.prismarin.api.event.Cancellable;
import net.prismarin.api.event.PrismarinEvent;

public final class ClanCreateEvent implements PrismarinEvent, Cancellable {

    private final UUID founder;
    private final String name;
    private final String tag;
    private volatile boolean cancelled;

    public ClanCreateEvent(UUID founder, String name, String tag) {
        this.founder = founder;
        this.name = name;
        this.tag = tag;
    }

    public UUID founder() {
        return founder;
    }

    public String name() {
        return name;
    }

    public String tag() {
        return tag;
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
