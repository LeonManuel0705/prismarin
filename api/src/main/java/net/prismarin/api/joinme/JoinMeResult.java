package net.prismarin.api.joinme;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public record JoinMeResult(Status status, Optional<JoinMe> joinMe, long waitMillis) {

    public enum Status {
        SUCCESS,
        DISABLED,
        NO_PERMISSION,
        OFFLINE,
        COOLDOWN,
        GROUP_BLOCKED,
        GAME_STATE,
        VANISHED,
        REFUSED,
        NOTHING_TO_JOIN,
        NOT_ENOUGH_MONEY,
        CANCELLED,
        NOT_FOUND,
        EXPIRED,
        USED_UP,
        OWN,
        ALREADY_THERE,
        OTHER_SERVER,
        SERVER_OFFLINE,
        UNAVAILABLE,
        FAILED;

        public boolean success() {
            return this == SUCCESS;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        public static Optional<Status> byId(String id) {
            for (Status status : values()) {
                if (status.id().equals(id)) {
                    return Optional.of(status);
                }
            }
            return Optional.empty();
        }
    }

    public JoinMeResult {
        Objects.requireNonNull(status, "status");
        joinMe = joinMe == null ? Optional.empty() : joinMe;
        waitMillis = Math.max(0L, waitMillis);
    }

    public static JoinMeResult of(Status status) {
        return new JoinMeResult(status, Optional.empty(), 0L);
    }

    public static JoinMeResult of(Status status, JoinMe joinMe) {
        return new JoinMeResult(status, Optional.ofNullable(joinMe), 0L);
    }

    public static JoinMeResult success(JoinMe joinMe) {
        return of(Status.SUCCESS, joinMe);
    }

    public static JoinMeResult cooldown(long waitMillis) {
        return new JoinMeResult(Status.COOLDOWN, Optional.empty(), waitMillis);
    }

    public boolean success() {
        return status.success();
    }
}
