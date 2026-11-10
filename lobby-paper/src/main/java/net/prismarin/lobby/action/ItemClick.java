package net.prismarin.lobby.action;

import java.util.Locale;
import java.util.Optional;

public enum ItemClick {
    RIGHT_CLICK("right-click"),
    LEFT_CLICK("left-click"),
    SHIFT_RIGHT_CLICK("shift-right-click"),
    SHIFT_LEFT_CLICK("shift-left-click");

    private final String id;

    ItemClick(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public ItemClick base() {
        return switch (this) {
            case SHIFT_RIGHT_CLICK -> RIGHT_CLICK;
            case SHIFT_LEFT_CLICK -> LEFT_CLICK;
            default -> this;
        };
    }

    public static ItemClick of(boolean left, boolean sneaking) {
        if (left) {
            return sneaking ? SHIFT_LEFT_CLICK : LEFT_CLICK;
        }
        return sneaking ? SHIFT_RIGHT_CLICK : RIGHT_CLICK;
    }

    public static Optional<ItemClick> byId(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
        for (ItemClick click : values()) {
            if (click.id.equals(normalized) || click.id.replace("-click", "").equals(normalized)) {
                return Optional.of(click);
            }
        }
        return Optional.empty();
    }
}
