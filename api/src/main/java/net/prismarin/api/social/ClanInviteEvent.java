package net.prismarin.api.social;

import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record ClanInviteEvent(Clan clan, UUID inviter, UUID target) implements PrismarinEvent {
}
