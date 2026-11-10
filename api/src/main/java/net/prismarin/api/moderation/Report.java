package net.prismarin.api.moderation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Report(String id, UUID target, String targetName, String shownName, String server, String context,
                     List<Reporter> reporters, List<String> chat, long createdAt, String holder, long claimedAt,
                     boolean open, String closedBy, long closedAt, ReportVerdict verdict) {

    public record Reporter(UUID uuid, String name, String reason, long at) {

        public Reporter {
            Objects.requireNonNull(uuid, "uuid");
            name = name == null ? "" : name;
            reason = reason == null ? "" : reason;
        }
    }

    public Report {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(target, "target");
        targetName = targetName == null ? "" : targetName;
        shownName = shownName == null || shownName.isBlank() ? targetName : shownName;
        server = server == null ? "" : server;
        context = context == null ? "" : context;
        reporters = List.copyOf(reporters);
        if (reporters.isEmpty()) {
            throw new IllegalArgumentException("a report needs at least one reporter");
        }
        chat = chat == null ? List.of() : List.copyOf(chat);
        holder = holder == null ? "" : holder;
        closedBy = closedBy == null ? "" : closedBy;
        if (open) {
            verdict = null;
        }
    }

    public String reason() {
        return reporters.getFirst().reason();
    }

    public int reporterCount() {
        return reporters.size();
    }

    public boolean inProgress() {
        return open && !holder.isEmpty();
    }

    public Optional<ReportVerdict> closedAs() {
        return Optional.ofNullable(verdict);
    }
}
