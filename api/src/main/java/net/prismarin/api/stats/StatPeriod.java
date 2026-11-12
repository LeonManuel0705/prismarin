package net.prismarin.api.stats;

import java.util.Locale;
import java.util.Optional;

public enum StatPeriod {
    ALL_TIME("alltime"),
    WEEKLY("weekly"),
    MONTHLY("monthly");

    private final String id;

    StatPeriod(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<StatPeriod> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return switch (id.strip().toLowerCase(Locale.ROOT)) {
            case "alltime", "all-time", "all", "total" -> Optional.of(ALL_TIME);
            case "weekly", "week" -> Optional.of(WEEKLY);
            case "monthly", "month" -> Optional.of(MONTHLY);
            default -> Optional.empty();
        };
    }
}
