package net.prismarin.api.moderation;

import net.prismarin.api.event.PrismarinEvent;

public record ReportReleasedEvent(Report report, String staff) implements PrismarinEvent {
}
