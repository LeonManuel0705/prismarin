package net.prismarin.api.cosmetics;

import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.Cancellable;
import net.prismarin.api.event.PrismarinEvent;

public final class CosmeticEffectEvent implements PrismarinEvent, Cancellable {

    public enum Trigger {
        API,
        KILL,
        PROJECTILE,
        COMMAND
    }

    private final UUID player;
    private final Cosmetic cosmetic;
    private final CosmeticSpot spot;
    private final Trigger trigger;
    private volatile boolean cancelled;

    public CosmeticEffectEvent(UUID player, Cosmetic cosmetic, CosmeticSpot spot, Trigger trigger) {
        this.player = Objects.requireNonNull(player, "player");
        this.cosmetic = Objects.requireNonNull(cosmetic, "cosmetic");
        this.spot = Objects.requireNonNull(spot, "spot");
        this.trigger = Objects.requireNonNull(trigger, "trigger");
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

    public CosmeticSpot spot() {
        return spot;
    }

    public Trigger trigger() {
        return trigger;
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
