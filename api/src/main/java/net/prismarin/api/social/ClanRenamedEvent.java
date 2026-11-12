package net.prismarin.api.social;

import net.prismarin.api.event.PrismarinEvent;

public record ClanRenamedEvent(Clan clan, String previousName, String previousTag) implements PrismarinEvent {
}
