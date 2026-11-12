package net.prismarin.api.social;

import java.util.Locale;
import java.util.Optional;

public enum SocialResult {
    SUCCESS,
    NOT_FOUND,
    SELF,
    NOT_IN_PARTY,
    NOT_ALLOWED,
    NOT_MEMBER,
    ALREADY_MEMBER,
    ALREADY_IN_PARTY,
    TARGET_IN_PARTY,
    ALREADY_INVITED,
    NO_INVITE,
    CLOSED,
    FULL,
    NOT_ACCEPTING,
    ALREADY_ROLE,
    UNCHANGED,
    ALREADY_FRIENDS,
    ALREADY_SENT,
    NOT_FRIENDS,
    LIMIT_REACHED,
    TARGET_FULL,
    COOLDOWN,
    DISABLED,
    UNAVAILABLE,
    NOT_IN_CLAN,
    ALREADY_IN_CLAN,
    TARGET_IN_CLAN,
    NAME_TAKEN,
    TAG_TAKEN,
    INVALID_NAME,
    INVALID_TAG,
    INVALID_COLOR,
    TRANSFER_FIRST,
    INVALID_DATA,
    CANCELLED,
    FAILED;

    public boolean success() {
        return this == SUCCESS;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<SocialResult> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (SocialResult result : values()) {
            if (result.id().equals(id)) {
                return Optional.of(result);
            }
        }
        return Optional.empty();
    }
}
