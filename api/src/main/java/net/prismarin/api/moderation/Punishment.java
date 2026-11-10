package net.prismarin.api.moderation;

import java.util.Objects;
import java.util.UUID;

public record Punishment(String id, UUID target, String targetName, PunishmentType type, String reason, String actor,
                         long createdAt, long expiresAt, boolean active, String revokedBy, long revokedAt,
                         String template, String note, String addressHash, String revokeReason, String replacedBy) {

    public Punishment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(type, "type");
        targetName = targetName == null ? "" : targetName;
        reason = reason == null ? "" : reason;
        actor = actor == null ? "" : actor;
        revokedBy = revokedBy == null ? "" : revokedBy;
        template = template == null ? "" : template;
        note = note == null ? "" : note;
        addressHash = addressHash == null ? "" : addressHash;
        revokeReason = revokeReason == null ? "" : revokeReason;
        replacedBy = replacedBy == null ? "" : replacedBy;
    }

    public Punishment(String id, UUID target, String targetName, PunishmentType type, String reason, String actor,
                      long createdAt, long expiresAt, boolean active, String revokedBy, long revokedAt) {
        this(id, target, targetName, type, reason, actor, createdAt, expiresAt, active, revokedBy, revokedAt,
                "", "", "", "", "");
    }

    public boolean permanent() {
        return expiresAt <= 0L;
    }

    public boolean inForce(long now) {
        return active && type.lasting() && (permanent() || expiresAt > now);
    }

    public boolean addressBound() {
        return !addressHash.isEmpty();
    }

    public boolean fromTemplate() {
        return !template.isEmpty();
    }

    public boolean lifted() {
        return revokedAt > 0L && replacedBy.isEmpty();
    }

    public boolean replaced() {
        return !replacedBy.isEmpty();
    }

    public long length() {
        return permanent() ? 0L : Math.max(0L, expiresAt - createdAt);
    }

    public Punishment revoke(String by, long at) {
        return revoke(by, at, "");
    }

    public Punishment revoke(String by, long at, String why) {
        return new Punishment(id, target, targetName, type, reason, actor, createdAt, expiresAt, false, by, at,
                template, note, addressHash, why, "");
    }

    public Punishment replace(String by, long at, String successor) {
        return new Punishment(id, target, targetName, type, reason, actor, createdAt, expiresAt, false, by, at,
                template, note, addressHash, "", successor);
    }
}
