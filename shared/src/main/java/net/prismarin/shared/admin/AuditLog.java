package net.prismarin.shared.admin;

import com.google.gson.Gson;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.prismarin.shared.storage.DocumentStore;

public final class AuditLog {

    static final String COLLECTION = "audit";

    public record Entry(String id, long at, String actor, String action, String target, String details) {
    }

    private final DocumentStore documents;
    private final Executor executor;
    private final Clock clock;
    private final Gson gson = new Gson();

    public AuditLog(DocumentStore documents, Executor executor, Clock clock) {
        this.documents = documents;
        this.executor = executor;
        this.clock = clock;
    }

    public void record(String actor, String action, String target, String details) {
        long now = clock.millis();
        Entry entry = new Entry(now + "-" + UUID.randomUUID().toString().substring(0, 8), now, actor, action,
                target == null ? "" : target, details == null ? "" : details);
        executor.execute(() -> documents.save(COLLECTION, entry.id(), gson.toJson(entry)));
    }

    public CompletableFuture<List<Entry>> latest(String target, int limit) {
        return CompletableFuture.supplyAsync(() -> (target == null
                ? documents.findAll(COLLECTION)
                : documents.findBy(COLLECTION, "target", target)).stream()
                .map(json -> gson.fromJson(json, Entry.class))
                .sorted(Comparator.comparingLong(Entry::at).reversed())
                .limit(limit)
                .toList(), executor);
    }
}
