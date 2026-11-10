package net.prismarin.api.social;

import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record PartyJoinedEvent(Party party, UUID player) implements PrismarinEvent {
}
