package net.prismarin.api.social;

import net.prismarin.api.event.PrismarinEvent;

public record ClanDisbandedEvent(Clan clan) implements PrismarinEvent {
}
