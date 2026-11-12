package net.prismarin.api.stats;

import java.util.Locale;
import java.util.Optional;

public enum StatOrder {
    HIGHEST("highest"),
    LOWEST("lowest");

    private final String id;

    StatOrder(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean better(long candidate, long current) {
        return this == HIGHEST ? candidate > current : candidate < current;
    }

    public int compare(long first, long second) {
        return this == HIGHEST ? Long.compare(second, first) : Long.compare(first, second);
    }

    public static Optional<StatOrder> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return switch (id.strip().toLowerCase(Locale.ROOT)) {
            case "highest", "high", "desc", "descending" -> Optional.of(HIGHEST);
            case "lowest", "low", "asc", "ascending" -> Optional.of(LOWEST);
            default -> Optional.empty();
        };
    }
}
