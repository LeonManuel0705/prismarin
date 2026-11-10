package net.prismarin.api.moderation;

import java.util.Objects;
import java.util.UUID;

public record Punishment(String id, UUID target, String targetName, PunishmentType type, String reason, String actor,
                         long createdAt, long expiresAt, boolean active, String revokedBy, long revokedAt) {

    public Punishment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(type, "type");
        targetName = targetName == null ? "" : targetName;
        reason = reason == null ? "" : reason;
        actor = actor == null ? "" : actor;
        revokedBy = revokedBy == null ? "" : revokedBy;
    }

    public boolean permanent() {
        return expiresAt <= 0L;
    }

    public boolean inForce(long now) {
        return active && type.lasting() && (permanent() || expiresAt > now);
    }

    public Punishment revoke(String by, long at) {
        return new Punishment(id, target, targetName, type, reason, actor, createdAt, expiresAt, false, by, at);
    }
}
