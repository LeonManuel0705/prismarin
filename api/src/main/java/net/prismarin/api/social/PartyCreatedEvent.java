package net.prismarin.api.social;

import net.prismarin.api.event.PrismarinEvent;

public record PartyCreatedEvent(Party party) implements PrismarinEvent {
}
