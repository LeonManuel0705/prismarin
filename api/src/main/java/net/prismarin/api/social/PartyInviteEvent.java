package net.prismarin.api.social;

import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record PartyInviteEvent(Party party, UUID inviter, UUID target) implements PrismarinEvent {
}
