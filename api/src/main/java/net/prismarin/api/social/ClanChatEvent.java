package net.prismarin.api.social;

import java.util.UUID;
import net.prismarin.api.event.Cancellable;
import net.prismarin.api.event.PrismarinEvent;

public final class ClanChatEvent implements PrismarinEvent, Cancellable {

    private final Clan clan;
    private final UUID sender;
    private final String message;
    private volatile boolean cancelled;

    public ClanChatEvent(Clan clan, UUID sender, String message) {
        this.clan = clan;
        this.sender = sender;
        this.message = message;
    }

    public Clan clan() {
        return clan;
    }

    public UUID sender() {
        return sender;
    }

    public String message() {
        return message;
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
