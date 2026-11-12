package net.prismarin.api.joinme;

import net.prismarin.api.event.PrismarinEvent;

public record JoinMeCreatedEvent(JoinMe joinMe) implements PrismarinEvent {
}
