package net.prismarin.api.moderation;

import net.prismarin.api.event.PrismarinEvent;

public record ReportFiledEvent(Report report) implements PrismarinEvent {
}
