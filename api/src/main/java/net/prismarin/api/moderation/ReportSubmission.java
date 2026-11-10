package net.prismarin.api.moderation;

import java.util.Objects;

public record ReportSubmission(Report report, ReportOutcome outcome) {

    public ReportSubmission {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(outcome, "outcome");
    }
}
