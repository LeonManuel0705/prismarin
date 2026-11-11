package net.prismarin.api.admin;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AuditService {

    CompletableFuture<Void> record(String actor, String action, String target, String details);

    default CompletableFuture<Void> record(String actor, String action, String target) {
        return record(actor, action, target, "");
    }

    CompletableFuture<List<AuditEntry>> latest(String target, int limit);

    default CompletableFuture<List<AuditEntry>> latest(int limit) {
        return latest(null, limit);
    }
}
