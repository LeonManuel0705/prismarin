package net.prismarin.api.moderation;

import java.util.Locale;
import java.util.Optional;

public enum ChatFilterAction {
    BLOCK,
    REPLACE,
    WARN,
    LOG;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ChatFilterAction> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        for (ChatFilterAction action : values()) {
            if (action.id().equals(key)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }
}
