package net.prismarin.api.nick;

import java.util.Objects;

public record NickResult(Status status, String name) {

    public NickResult {
        Objects.requireNonNull(status, "status");
        name = name == null ? "" : name;
    }

    public static NickResult of(Status status) {
        return new NickResult(status, "");
    }

    public static NickResult of(Status status, String name) {
        return new NickResult(status, name);
    }

    public boolean success() {
        return status == Status.APPLIED || status == Status.STORED || status == Status.REMOVED;
    }

    public enum Status {
        APPLIED,
        STORED,
        REMOVED,
        NOT_NICKED,
        INVALID_NAME,
        NAME_TAKEN,
        NO_FREE_NAME,
        NOT_ONLINE,
        BUSY,
        DISABLED,
        FAILED
    }
}
