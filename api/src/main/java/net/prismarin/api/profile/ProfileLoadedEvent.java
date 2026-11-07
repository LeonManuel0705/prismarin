package net.prismarin.api.profile;

import net.prismarin.api.event.PrismarinEvent;

public record ProfileLoadedEvent(PlayerProfile profile, boolean created) implements PrismarinEvent {
}
