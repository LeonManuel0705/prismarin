package net.prismarin.api.admin;

import java.util.regex.Pattern;

public record AuditEntry(String id, long at, String actor, String action, String target, String details) {

    public static final Pattern ACTION = Pattern.compile("[a-z0-9][a-z0-9_.:-]{0,63}");
    public static final int MAX_ACTOR = 64;
    public static final int MAX_TARGET = 128;
    public static final int MAX_DETAILS = 1024;
    public static final int MAX_LIMIT = 100;

    public AuditEntry {
        id = id == null ? "" : id;
        actor = actor == null ? "" : actor;
        action = action == null ? "" : action;
        target = target == null ? "" : target;
        details = details == null ? "" : details;
    }

    public static void check(String actor, String action, String target, String details) {
        if (actor == null || actor.isBlank() || actor.length() > MAX_ACTOR) {
            throw new IllegalArgumentException("an audit actor needs 1 to " + MAX_ACTOR + " characters");
        }
        if (action == null || !ACTION.matcher(action).matches()) {
            throw new IllegalArgumentException("audit action '" + action + "' must match " + ACTION.pattern());
        }
        if (target != null && target.length() > MAX_TARGET) {
            throw new IllegalArgumentException("an audit target has at most " + MAX_TARGET + " characters");
        }
        if (details != null && details.length() > MAX_DETAILS) {
            throw new IllegalArgumentException("audit details have at most " + MAX_DETAILS + " characters");
        }
    }

    public static int limit(int requested) {
        return Math.max(1, Math.min(MAX_LIMIT, requested));
    }
}
