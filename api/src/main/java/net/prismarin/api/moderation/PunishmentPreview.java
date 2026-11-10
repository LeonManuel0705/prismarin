package net.prismarin.api.moderation;

import java.util.Objects;
import java.util.UUID;

public record PunishmentPreview(UUID target, String template, int previousOffences, int level, int levels,
                                PunishmentTemplate.Step step) {

    public PunishmentPreview {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(step, "step");
    }
}
