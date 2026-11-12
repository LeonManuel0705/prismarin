package net.prismarin.api.social;

import java.util.UUID;
import net.prismarin.api.event.Cancellable;
import net.prismarin.api.event.PrismarinEvent;

public final class ClanJoinEvent implements PrismarinEvent, Cancellable {

    private final Clan clan;
    private final UUID player;
    private volatile boolean cancelled;

    public ClanJoinEvent(Clan clan, UUID player) {
        this.clan = clan;
        this.player = player;
    }

    public Clan clan() {
        return clan;
    }

    public UUID player() {
        return player;
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
