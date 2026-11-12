package net.prismarin.api.moderation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record BannedAltJoinEvent(UUID player, String name, List<AltAccount> bannedAlts, boolean denied)
        implements PrismarinEvent {

    public BannedAltJoinEvent {
        Objects.requireNonNull(player, "player");
        name = name == null ? "" : name;
        bannedAlts = bannedAlts == null ? List.of() : List.copyOf(bannedAlts);
    }
}
