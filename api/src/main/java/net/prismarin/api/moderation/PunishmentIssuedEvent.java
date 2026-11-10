package net.prismarin.api.moderation;

import net.prismarin.api.event.PrismarinEvent;

public record PunishmentIssuedEvent(Punishment punishment) implements PrismarinEvent {
}
