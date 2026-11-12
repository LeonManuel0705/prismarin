package net.prismarin.api.social;

import net.prismarin.api.event.PrismarinEvent;

public record ClanColorChangedEvent(Clan clan, String previous) implements PrismarinEvent {
}
