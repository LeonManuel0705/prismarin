package net.prismarin.api.admin;

import net.prismarin.api.event.PrismarinEvent;

public record PrismarinReloadedEvent(ReloadResult result, String actor) implements PrismarinEvent {
}
