package net.prismarin.api.cosmetics;

import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record CosmeticUnequippedEvent(UUID player, String cosmetic, CosmeticSlot slot, Reason reason)
        implements PrismarinEvent {

    public enum Reason {
        UNEQUIPPED,
        REPLACED,
        REVOKED
    }

    public CosmeticUnequippedEvent {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(cosmetic, "cosmetic");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(reason, "reason");
    }
}
