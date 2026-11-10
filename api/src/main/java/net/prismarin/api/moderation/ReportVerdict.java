package net.prismarin.api.moderation;

import java.util.Locale;
import java.util.Optional;

public enum ReportVerdict {
    VALID,
    INVALID,
    PUNISHED;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ReportVerdict> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (ReportVerdict verdict : values()) {
            if (verdict.id().equalsIgnoreCase(id.trim())) {
                return Optional.of(verdict);
            }
        }
        return Optional.empty();
    }
}
