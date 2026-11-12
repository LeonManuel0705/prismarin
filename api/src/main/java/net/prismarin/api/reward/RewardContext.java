package net.prismarin.api.reward;

import java.util.Objects;
import java.util.UUID;

public record RewardContext(String source, UUID player, String name, int streak, String detail) {

    public static final String DAILY = "daily";
    public static final String VOTE = "vote";

    public RewardContext {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(player, "player");
        name = name == null ? "" : name;
        detail = detail == null ? "" : detail;
    }
}
