package net.prismarin.shared.admin;

import java.util.UUID;

public record Report(String id, UUID reporter, String reporterName, UUID target, String targetName, String reason,
                     String server, long createdAt, boolean open, String closedBy, long closedAt) {

    public Report close(String by, long at) {
        return new Report(id, reporter, reporterName, target, targetName, reason, server, createdAt, false, by, at);
    }
}
