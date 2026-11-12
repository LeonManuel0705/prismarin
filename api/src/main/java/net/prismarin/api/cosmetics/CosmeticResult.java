package net.prismarin.api.cosmetics;

import java.util.Objects;
import java.util.Optional;

public record CosmeticResult(Status status, Optional<Cosmetic> cosmetic, long missing) {

    public enum Status {
        DONE,
        UNKNOWN,
        CATEGORY_OFF,
        NOT_OWNED,
        ALREADY_OWNED,
        ALREADY_EQUIPPED,
        NOT_EQUIPPED,
        NOT_FOR_SALE,
        NOT_ENOUGH_MONEY,
        CANCELLED,
        FAILED
    }

    public CosmeticResult {
        Objects.requireNonNull(status, "status");
        cosmetic = cosmetic == null ? Optional.empty() : cosmetic;
        missing = Math.max(0L, missing);
    }

    public static CosmeticResult of(Status status, Cosmetic cosmetic) {
        return new CosmeticResult(status, Optional.ofNullable(cosmetic), 0L);
    }

    public static CosmeticResult of(Status status) {
        return new CosmeticResult(status, Optional.empty(), 0L);
    }

    public static CosmeticResult missing(Cosmetic cosmetic, long missing) {
        return new CosmeticResult(Status.NOT_ENOUGH_MONEY, Optional.ofNullable(cosmetic), missing);
    }

    public boolean success() {
        return status == Status.DONE;
    }
}
