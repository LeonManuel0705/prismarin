package net.prismarin.api.social;

import net.prismarin.api.event.PrismarinEvent;

public record PartyDisbandedEvent(Party party) implements PrismarinEvent {
}
