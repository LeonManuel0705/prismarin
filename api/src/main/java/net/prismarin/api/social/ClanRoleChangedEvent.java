package net.prismarin.api.social;

import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record ClanRoleChangedEvent(Clan clan, UUID player, ClanRole previous, ClanRole current)
        implements PrismarinEvent {
}
