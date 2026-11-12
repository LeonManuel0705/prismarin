package net.prismarin.api.social;

import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record ClanMemberJoinedEvent(Clan clan, UUID player) implements PrismarinEvent {
}
