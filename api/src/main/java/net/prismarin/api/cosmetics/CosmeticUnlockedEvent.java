package net.prismarin.api.cosmetics;

import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record CosmeticUnlockedEvent(UUID player, Cosmetic cosmetic, CosmeticSource source) implements PrismarinEvent {

    public CosmeticUnlockedEvent {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(cosmetic, "cosmetic");
        Objects.requireNonNull(source, "source");
    }
}
