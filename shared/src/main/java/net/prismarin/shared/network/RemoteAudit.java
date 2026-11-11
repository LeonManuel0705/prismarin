package net.prismarin.shared.network;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.admin.AuditEntry;
import net.prismarin.api.admin.AuditService;

public final class RemoteAudit implements AuditService {

    private final AuditService local;
    private final ProxyLink link;
    private final boolean sharedStorage;

    public RemoteAudit(AuditService local, ProxyLink link, boolean sharedStorage) {
        this.local = local;
        this.link = link;
        this.sharedStorage = sharedStorage;
    }

    @Override
    public CompletableFuture<Void> record(String actor, String action, String target, String details) {
        AuditEntry.check(actor, action, target, details);
        if (sharedStorage) {
            return local.record(actor, action, target, details);
        }
        JsonObject body = new JsonObject();
        body.addProperty("actor", actor);
        body.addProperty("action", action);
        body.addProperty("target", target == null ? "" : target);
        body.addProperty("details", details == null ? "" : details);
        return ProxyLink.orNoRoute(link.call(NetworkCalls.Action.AUDIT_RECORD, body).thenApply(answer -> (Void) null),
                () -> local.record(actor, action, target, details));
    }

    @Override
    public CompletableFuture<List<AuditEntry>> latest(String target, int limit) {
        if (sharedStorage) {
            return local.latest(target, limit);
        }
        JsonObject body = new JsonObject();
        body.addProperty("target", target == null ? "" : target);
        body.addProperty("limit", AuditEntry.limit(limit));
        return ProxyLink.orNoRoute(link.call(NetworkCalls.Action.AUDIT_LATEST, body)
                .thenApply(answer -> ApiJson.list(answer.get("entries"), AuditEntry.class)),
                () -> local.latest(target, limit));
    }
}
