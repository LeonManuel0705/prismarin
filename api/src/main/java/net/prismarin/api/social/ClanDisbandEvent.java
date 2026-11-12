package net.prismarin.api.social;

import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.event.Cancellable;
import net.prismarin.api.event.PrismarinEvent;

public final class ClanDisbandEvent implements PrismarinEvent, Cancellable {

    private final Clan clan;
    private final UUID actor;
    private volatile boolean cancelled;

    public ClanDisbandEvent(Clan clan, UUID actor) {
        this.clan = clan;
        this.actor = actor;
    }

    public Clan clan() {
        return clan;
    }

    public Optional<UUID> actor() {
        return Optional.ofNullable(actor);
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
