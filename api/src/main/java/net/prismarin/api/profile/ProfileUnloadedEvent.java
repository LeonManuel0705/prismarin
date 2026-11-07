package net.prismarin.api.profile;

import net.prismarin.api.event.PrismarinEvent;

public record ProfileUnloadedEvent(PlayerProfile profile) implements PrismarinEvent {
}
