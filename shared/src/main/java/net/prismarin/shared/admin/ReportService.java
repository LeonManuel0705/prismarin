package net.prismarin.shared.admin;

import com.google.gson.Gson;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import net.prismarin.shared.storage.DocumentStore;

public final class ReportService {

    static final String COLLECTION = "reports";

    private final DocumentStore documents;
    private final Executor executor;
    private final Clock clock;
    private final Gson gson = new Gson();

    public ReportService(DocumentStore documents, Executor executor, Clock clock) {
        this.documents = documents;
        this.executor = executor;
        this.clock = clock;
    }

    public CompletableFuture<Report> file(UUID reporter, String reporterName, UUID target, String targetName,
                                          String reason, String server) {
        return CompletableFuture.supplyAsync(() -> {
            Report report = new Report(Integer.toString(ThreadLocalRandom.current().nextInt(10_000, 99_999)),
                    reporter, reporterName, target, targetName, reason, server, clock.millis(), true, "", 0L);
            documents.save(COLLECTION, report.id(), gson.toJson(report));
            return report;
        }, executor);
    }

    public CompletableFuture<List<Report>> open() {
        return CompletableFuture.supplyAsync(() -> documents.findAll(COLLECTION).stream()
                .map(json -> gson.fromJson(json, Report.class))
                .filter(Report::open)
                .sorted(Comparator.comparingLong(Report::createdAt))
                .toList(), executor);
    }

    public CompletableFuture<Long> countAgainst(UUID target) {
        return CompletableFuture.supplyAsync(() -> (long) documents.findBy(COLLECTION, "target", target.toString())
                .size(), executor);
    }

    public CompletableFuture<Optional<Report>> close(String id, String by) {
        return CompletableFuture.supplyAsync(() -> documents.find(COLLECTION, id)
                .map(json -> gson.fromJson(json, Report.class))
                .filter(Report::open)
                .map(report -> {
                    Report closed = report.close(by, clock.millis());
                    documents.save(COLLECTION, closed.id(), gson.toJson(closed));
                    return closed;
                }), executor);
    }
}
