package net.prismarin.api.cosmetics;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.event.Cancellable;
import net.prismarin.api.event.PrismarinEvent;

public final class CosmeticEquipEvent implements PrismarinEvent, Cancellable {

    private final UUID player;
    private final Cosmetic cosmetic;
    private final String previous;
    private volatile boolean cancelled;

    public CosmeticEquipEvent(UUID player, Cosmetic cosmetic, String previous) {
        this.player = Objects.requireNonNull(player, "player");
        this.cosmetic = Objects.requireNonNull(cosmetic, "cosmetic");
        this.previous = previous;
    }

    public UUID player() {
        return player;
    }

    public Cosmetic cosmetic() {
        return cosmetic;
    }

    public CosmeticSlot slot() {
        return cosmetic.slot();
    }

    public Optional<String> previous() {
        return Optional.ofNullable(previous);
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
