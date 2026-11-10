package net.prismarin.api.social;

import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record FriendRequestEvent(UUID sender, UUID target) implements PrismarinEvent {
}
