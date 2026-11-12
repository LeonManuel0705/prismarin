package net.prismarin.api.social;

import net.prismarin.api.event.PrismarinEvent;

public record ClanAccessChangedEvent(Clan clan, ClanAccess previous) implements PrismarinEvent {
}
