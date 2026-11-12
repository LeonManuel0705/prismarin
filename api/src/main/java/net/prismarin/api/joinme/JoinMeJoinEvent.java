package net.prismarin.api.joinme;

import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.Cancellable;
import net.prismarin.api.event.PrismarinEvent;

public final class JoinMeJoinEvent implements PrismarinEvent, Cancellable {

    private final UUID player;
    private final JoinMe joinMe;
    private volatile boolean cancelled;

    public JoinMeJoinEvent(UUID player, JoinMe joinMe) {
        this.player = Objects.requireNonNull(player, "player");
        this.joinMe = Objects.requireNonNull(joinMe, "joinMe");
    }

    public UUID player() {
        return player;
    }

    public JoinMe joinMe() {
        return joinMe;
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
