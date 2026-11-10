package net.prismarin.api.social;

import net.prismarin.api.event.PrismarinEvent;

public record PartySettingsChangedEvent(Party party, PartySettings previous) implements PrismarinEvent {
}
