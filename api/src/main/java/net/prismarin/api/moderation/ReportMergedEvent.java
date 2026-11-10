package net.prismarin.api.moderation;

import net.prismarin.api.event.PrismarinEvent;

public record ReportMergedEvent(Report report, Report.Reporter reporter) implements PrismarinEvent {
}
