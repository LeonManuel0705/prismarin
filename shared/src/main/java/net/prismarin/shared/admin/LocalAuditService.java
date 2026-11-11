package net.prismarin.shared.admin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.admin.AuditEntry;
import net.prismarin.api.admin.AuditService;

public final class LocalAuditService implements AuditService {

    private final AuditLog log;

    public LocalAuditService(AuditLog log) {
        this.log = log;
    }

    @Override
    public CompletableFuture<Void> record(String actor, String action, String target, String details) {
        AuditEntry.check(actor, action, target, details);
        return log.store(actor, action, target, details);
    }

    @Override
    public CompletableFuture<List<AuditEntry>> latest(String target, int limit) {
        return log.latest(target == null || target.isBlank() ? null : target, AuditEntry.limit(limit))
                .thenApply(entries -> entries.stream().map(LocalAuditService::toApi).toList());
    }

    static AuditEntry toApi(AuditLog.Entry entry) {
        return new AuditEntry(entry.id(), entry.at(), entry.actor(), entry.action(), entry.target(), entry.details());
    }
}
