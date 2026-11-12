package net.prismarin.api.cosmetics;

import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.Cancellable;
import net.prismarin.api.event.PrismarinEvent;

public final class CosmeticPurchaseEvent implements PrismarinEvent, Cancellable {

    private final UUID player;
    private final Cosmetic cosmetic;
    private final CosmeticPrice price;
    private volatile boolean cancelled;

    public CosmeticPurchaseEvent(UUID player, Cosmetic cosmetic, CosmeticPrice price) {
        this.player = Objects.requireNonNull(player, "player");
        this.cosmetic = Objects.requireNonNull(cosmetic, "cosmetic");
        this.price = Objects.requireNonNull(price, "price");
    }

    public UUID player() {
        return player;
    }

    public Cosmetic cosmetic() {
        return cosmetic;
    }

    public CosmeticPrice price() {
        return price;
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
