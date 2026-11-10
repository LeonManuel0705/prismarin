package net.prismarin.shared.admin;

import com.google.gson.Gson;
import java.time.Clock;
import java.util.ArrayList;
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
    static final String FEEDBACK = "report-feedback";

    public enum Outcome {
        NEW,
        MERGED,
        DUPLICATE
    }

    public record Draft(UUID reporter, String reporterName, UUID target, String targetName, String reason,
                        String server, List<String> chat, String context, String shownName) {

        public Draft {
            chat = chat == null ? List.of() : List.copyOf(chat);
            context = context == null ? "" : context;
            shownName = shownName == null || shownName.isBlank() ? targetName : shownName;
        }

        public Draft(UUID reporter, String reporterName, UUID target, String targetName, String reason,
                     String server, List<String> chat, String context) {
            this(reporter, reporterName, target, targetName, reason, server, chat, context, targetName);
        }
    }

    public record Submission(Report report, Outcome outcome) {
    }

    private final DocumentStore documents;
    private final Executor executor;
    private final Clock clock;
    private final Gson gson = new Gson();
    private final Object lock = new Object();

    public ReportService(DocumentStore documents, Executor executor, Clock clock) {
        this.documents = documents;
        this.executor = executor;
        this.clock = clock;
    }

    public CompletableFuture<Report> file(UUID reporter, String reporterName, UUID target, String targetName,
                                          String reason, String server) {
        return file(reporter, reporterName, target, targetName, reason, server, List.of());
    }

    public CompletableFuture<Report> file(UUID reporter, String reporterName, UUID target, String targetName,
                                          String reason, String server, List<String> chat) {
        return submit(new Draft(reporter, reporterName, target, targetName, reason, server, chat, ""), 0L)
                .thenApply(Submission::report);
    }

    public CompletableFuture<Submission> submit(Draft draft, long mergeWindowMillis) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock) {
                long now = clock.millis();
                Optional<Report> existing = mergeWindowMillis <= 0 ? Optional.empty() : openAgainstNow(draft.target())
                        .stream()
                        .filter(report -> now - report.createdAt() <= mergeWindowMillis)
                        .max(Comparator.comparingLong(Report::createdAt));
                if (existing.isPresent()) {
                    Report report = existing.get();
                    if (report.reportedBy(draft.reporter())) {
                        return new Submission(report, Outcome.DUPLICATE);
                    }
                    Report merged = report.support(new Report.Supporter(draft.reporter(), draft.reporterName(),
                            draft.reason(), now), draft.chat());
                    store(merged);
                    return new Submission(merged, Outcome.MERGED);
                }
                Report report = new Report(Integer.toString(ThreadLocalRandom.current().nextInt(10_000, 99_999)),
                        draft.reporter(), draft.reporterName(), draft.target(), draft.targetName(), draft.reason(),
                        draft.server(), now, true, "", 0L, draft.chat(), List.of(), "", draft.context(), "",
                        draft.shownName());
                store(report);
                return new Submission(report, Outcome.NEW);
            }
        }, executor);
    }

    public CompletableFuture<List<Report>> open() {
        return CompletableFuture.supplyAsync(() -> all().stream()
                .filter(Report::open)
                .sorted(Comparator.comparingInt(Report::reporters).reversed()
                        .thenComparingLong(Report::createdAt))
                .toList(), executor);
    }

    public CompletableFuture<List<Report>> openAgainst(UUID target) {
        return CompletableFuture.supplyAsync(() -> openAgainstNow(target), executor);
    }

    public CompletableFuture<Optional<Report>> find(String id) {
        return CompletableFuture.supplyAsync(() -> documents.find(COLLECTION, id)
                .map(json -> gson.fromJson(json, Report.class)), executor);
    }

    public CompletableFuture<Long> countAgainst(UUID target) {
        return CompletableFuture.supplyAsync(() -> (long) documents.findBy(COLLECTION, "target", target.toString())
                .size(), executor);
    }

    public CompletableFuture<Optional<Report>> claim(String id, String by) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock) {
                return documents.find(COLLECTION, id)
                        .map(json -> gson.fromJson(json, Report.class))
                        .filter(Report::open)
                        .map(report -> {
                            Report claimed = report.claim(by);
                            store(claimed);
                            return claimed;
                        });
            }
        }, executor);
    }

    public CompletableFuture<Optional<Report>> close(String id, String by) {
        return close(id, by, Report.VALID);
    }

    public CompletableFuture<Optional<Report>> close(String id, String by, String verdict) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock) {
                return documents.find(COLLECTION, id)
                        .map(json -> gson.fromJson(json, Report.class))
                        .filter(Report::open)
                        .map(report -> {
                            Report closed = report.close(by, clock.millis(), verdict);
                            store(closed);
                            return closed;
                        });
            }
        }, executor);
    }

    public CompletableFuture<List<Report>> closeAllAgainst(UUID target, String by, String verdict) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock) {
                List<Report> closed = new ArrayList<>();
                for (Report report : openAgainstNow(target)) {
                    Report done = report.close(by, clock.millis(), verdict);
                    store(done);
                    closed.add(done);
                }
                return List.copyOf(closed);
            }
        }, executor);
    }

    public CompletableFuture<Void> queueFeedback(List<UUID> reporters) {
        return CompletableFuture.runAsync(() -> {
            for (UUID reporter : reporters) {
                int count = documents.find(FEEDBACK, reporter.toString())
                        .map(json -> gson.fromJson(json, Feedback.class).count()).orElse(0);
                documents.save(FEEDBACK, reporter.toString(), gson.toJson(new Feedback(count + 1)));
            }
        }, executor);
    }

    public CompletableFuture<Integer> takeFeedback(UUID reporter) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<String> json = documents.find(FEEDBACK, reporter.toString());
            if (json.isEmpty()) {
                return 0;
            }
            documents.delete(FEEDBACK, reporter.toString());
            return gson.fromJson(json.get(), Feedback.class).count();
        }, executor);
    }

    private List<Report> openAgainstNow(UUID target) {
        return documents.findBy(COLLECTION, "target", target.toString()).stream()
                .map(json -> gson.fromJson(json, Report.class))
                .filter(Report::open)
                .sorted(Comparator.comparingLong(Report::createdAt))
                .toList();
    }

    private List<Report> all() {
        return documents.findAll(COLLECTION).stream().map(json -> gson.fromJson(json, Report.class)).toList();
    }

    private void store(Report report) {
        documents.save(COLLECTION, report.id(), gson.toJson(report));
    }

    private record Feedback(int count) {
    }
}
