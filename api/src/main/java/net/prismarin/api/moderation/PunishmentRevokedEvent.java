package net.prismarin.api.moderation;

import net.prismarin.api.event.PrismarinEvent;

public record PunishmentRevokedEvent(Punishment punishment) implements PrismarinEvent {
}
