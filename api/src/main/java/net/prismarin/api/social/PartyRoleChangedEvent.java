package net.prismarin.api.social;

import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record PartyRoleChangedEvent(Party party, UUID player, PartyRole previous, PartyRole current)
        implements PrismarinEvent {
}
