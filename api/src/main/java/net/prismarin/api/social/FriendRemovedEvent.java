package net.prismarin.api.social;

import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record FriendRemovedEvent(UUID player, UUID friend) implements PrismarinEvent {
}
