package net.prismarin.core.cosmetics;

import java.util.List;
import net.prismarin.api.cosmetics.CosmeticResult.Status;

public final class CosmeticMessages {

    public enum Action {
        PURCHASE,
        EQUIP,
        UNEQUIP,
        UNEQUIP_ALL,
        GIVE,
        TAKE
    }

    public static final List<String> GENERAL = List.of("cosmetics.unknown", "cosmetics.category-off",
            "cosmetics.cancelled", "cosmetics.failed");

    private CosmeticMessages() {
        throw new AssertionError();
    }

    public static String key(Action action, Status status) {
        return switch (status) {
            case UNKNOWN -> "cosmetics.unknown";
            case CATEGORY_OFF -> "cosmetics.category-off";
            case CANCELLED -> "cosmetics.cancelled";
            case FAILED -> "cosmetics.failed";
            case NOT_FOR_SALE -> "cosmetics.not-for-sale";
            case NOT_ENOUGH_MONEY -> "cosmetics.not-enough-money";
            case ALREADY_EQUIPPED -> "cosmetics.already-equipped";
            case NOT_EQUIPPED -> action == Action.UNEQUIP_ALL ? "cosmetics.nothing-equipped-all"
                    : "cosmetics.nothing-equipped";
            case NOT_OWNED -> action == Action.TAKE ? "cosmetics.taken-none" : "cosmetics.not-owned";
            case ALREADY_OWNED -> action == Action.GIVE ? "cosmetics.given-already" : "cosmetics.already-owned";
            case DONE -> switch (action) {
                case PURCHASE -> "cosmetics.purchased";
                case EQUIP -> "cosmetics.equipped";
                case UNEQUIP -> "cosmetics.unequipped";
                case UNEQUIP_ALL -> "cosmetics.unequipped-all";
                case GIVE -> "cosmetics.given";
                case TAKE -> "cosmetics.taken";
            };
        };
    }
}
