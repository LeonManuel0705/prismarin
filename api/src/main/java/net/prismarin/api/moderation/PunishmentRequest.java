package net.prismarin.api.moderation;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public record PunishmentRequest(UUID target, String targetName, PunishmentType type, String reason, String actor,
                                Duration duration, String template, String note, String addressHash) {

    public PunishmentRequest {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(type, "type");
        targetName = targetName == null ? "" : targetName;
        reason = reason == null ? "" : reason;
        actor = actor == null ? "" : actor;
        template = template == null ? "" : template;
        note = note == null ? "" : note;
        addressHash = addressHash == null ? "" : addressHash;
        if (duration != null && (duration.isZero() || duration.isNegative())) {
            duration = null;
        }
    }

    public static PunishmentRequest of(UUID target, String targetName, PunishmentType type, String reason,
                                       String actor, Duration duration) {
        return new PunishmentRequest(target, targetName, type, reason, actor, duration, "", "", "");
    }

    public PunishmentRequest withTemplate(String templateId, String templateNote) {
        return new PunishmentRequest(target, targetName, type, reason, actor, duration, templateId, templateNote,
                addressHash);
    }

    public PunishmentRequest withAddress(String hash) {
        return new PunishmentRequest(target, targetName, type, reason, actor, duration, template, note, hash);
    }
}
