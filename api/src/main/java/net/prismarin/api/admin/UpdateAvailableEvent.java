package net.prismarin.api.admin;

import net.prismarin.api.event.PrismarinEvent;

public record UpdateAvailableEvent(UpdateInfo update) implements PrismarinEvent {
}
