package net.prismarin.api.moderation;

import net.prismarin.api.event.PrismarinEvent;

public record ReportClaimedEvent(Report report, String staff, String previous) implements PrismarinEvent {
}
