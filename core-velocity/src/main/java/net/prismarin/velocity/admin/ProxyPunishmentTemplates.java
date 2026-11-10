package net.prismarin.velocity.admin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.PunishmentPreview;
import net.prismarin.api.moderation.PunishmentTemplates;

public final class ProxyPunishmentTemplates implements PunishmentTemplates {

    private final PunishmentFlow flow;

    public ProxyPunishmentTemplates(PunishmentFlow flow) {
        this.flow = flow;
    }

    @Override
    public List<net.prismarin.api.moderation.PunishmentTemplate> templates() {
        return flow.templates().stream().map(ProxyPunishmentTemplates::toApi).toList();
    }

    @Override
    public Optional<net.prismarin.api.moderation.PunishmentTemplate> template(String id) {
        return flow.template(id).map(ProxyPunishmentTemplates::toApi);
    }

    @Override
    public void register(net.prismarin.api.moderation.PunishmentTemplate template) {
        flow.register(fromApi(template));
    }

    @Override
    public void unregister(String id) {
        flow.unregister(id);
    }

    @Override
    public CompletableFuture<PunishmentPreview> preview(UUID target, String templateId) {
        return flow.preview(target, templateId).thenApply(outcome -> new PunishmentPreview(target,
                outcome.template().id(), outcome.previous(), outcome.level(), outcome.levels(),
                toApi(outcome.step())));
    }

    @Override
    public CompletableFuture<Punishment> punish(UUID target, String templateId, String actor, String note) {
        return flow.punish(target, templateId, actor == null || actor.isBlank() ? "Console" : actor, note);
    }

    static net.prismarin.api.moderation.PunishmentTemplate toApi(PunishmentTemplate template) {
        return new net.prismarin.api.moderation.PunishmentTemplate(template.id(), template.names(),
                template.descriptions(), template.icon(), template.permission(),
                template.steps().stream().map(ProxyPunishmentTemplates::toApi).toList(), template.forgetAfter());
    }

    static PunishmentTemplate fromApi(net.prismarin.api.moderation.PunishmentTemplate template) {
        return new PunishmentTemplate(template.id(), template.names(), template.icon(), template.permission(),
                template.steps().stream().map(step -> new PunishmentTemplate.Step(step.type(), step.duration()))
                        .toList(), template.forgetAfter(), template.descriptions());
    }

    private static net.prismarin.api.moderation.PunishmentTemplate.Step toApi(PunishmentTemplate.Step step) {
        return new net.prismarin.api.moderation.PunishmentTemplate.Step(step.type(), step.duration());
    }
}
