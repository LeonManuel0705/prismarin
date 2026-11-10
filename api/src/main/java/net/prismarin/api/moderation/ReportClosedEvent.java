package net.prismarin.api.moderation;

import net.prismarin.api.event.PrismarinEvent;

public record ReportClosedEvent(Report report, String staff, ReportVerdict verdict) implements PrismarinEvent {
}
