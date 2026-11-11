package net.prismarin.shared.network;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.PunishmentPreview;
import net.prismarin.api.moderation.PunishmentTemplate;
import net.prismarin.api.moderation.PunishmentTemplates;

public final class RemoteTemplates implements PunishmentTemplates {

    private final ProxyLink link;
    private final ModerationCatalog catalog;

    public RemoteTemplates(ProxyLink link, ModerationCatalog catalog) {
        this.link = link;
        this.catalog = catalog;
    }

    @Override
    public List<PunishmentTemplate> templates() {
        return catalog.templates();
    }

    @Override
    public Optional<PunishmentTemplate> template(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String wanted = id.trim().toLowerCase(Locale.ROOT);
        return templates().stream().filter(template -> template.id().equals(wanted)).findFirst();
    }

    @Override
    public void register(PunishmentTemplate template) {
        catalog.registerTemplate(Objects.requireNonNull(template, "template"));
    }

    @Override
    public void unregister(String id) {
        catalog.unregisterTemplate(Objects.requireNonNull(id, "id").trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public CompletableFuture<PunishmentPreview> preview(UUID target, String templateId) {
        JsonObject body = new JsonObject();
        body.addProperty("target", Objects.requireNonNull(target, "target").toString());
        body.addProperty("template", Objects.requireNonNull(templateId, "templateId"));
        return link.call(NetworkCalls.Action.TEMPLATE_PREVIEW, body)
                .thenApply(answer -> ApiJson.read(answer.get("preview"), PunishmentPreview.class));
    }

    @Override
    public CompletableFuture<Punishment> punish(UUID target, String templateId, String actor, String note) {
        JsonObject body = new JsonObject();
        body.addProperty("target", Objects.requireNonNull(target, "target").toString());
        body.addProperty("template", Objects.requireNonNull(templateId, "templateId"));
        body.addProperty("actor", actor == null ? "" : actor);
        body.addProperty("note", note == null ? "" : note);
        return link.call(NetworkCalls.Action.TEMPLATE_PUNISH, body, target)
                .thenApply(answer -> ApiJson.read(answer.get("punishment"), Punishment.class));
    }
}
