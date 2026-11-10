package net.prismarin.api.moderation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PunishmentTemplates {

    List<PunishmentTemplate> templates();

    Optional<PunishmentTemplate> template(String id);

    void register(PunishmentTemplate template);

    void unregister(String id);

    CompletableFuture<PunishmentPreview> preview(UUID target, String templateId);

    CompletableFuture<Punishment> punish(UUID target, String templateId, String actor, String note);

    default CompletableFuture<Punishment> punish(UUID target, String templateId, String actor) {
        return punish(target, templateId, actor, "");
    }
}
