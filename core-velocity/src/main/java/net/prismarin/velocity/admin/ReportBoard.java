package net.prismarin.velocity.admin;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.prismarin.shared.admin.Report;
import net.prismarin.velocity.social.Page;

public final class ReportBoard {

    public enum Filter {
        ALL,
        FREE,
        MINE,
        CHAT;

        public Filter next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Sort {
        URGENT,
        NEWEST,
        OLDEST;

        public Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record Counts(int open, int urgent, int progress, int free) {
    }

    private ReportBoard() {
        throw new AssertionError();
    }

    public static List<Report> arrange(List<Report> reports, Filter filter, Sort sort, String viewer, long now,
                                       long claimMillis) {
        return reports.stream()
                .filter(Report::open)
                .filter(matches(filter, viewer, now, claimMillis))
                .sorted(order(sort))
                .toList();
    }

    public static Page<Report> page(List<Report> reports, int page, int size) {
        return Page.of(reports, page, size);
    }

    public static Counts count(List<Report> reports, long now, long claimMillis, int urgentFrom) {
        int open = 0;
        int urgent = 0;
        int progress = 0;
        for (Report report : reports) {
            if (!report.open()) {
                continue;
            }
            open++;
            if (urgent(report, urgentFrom)) {
                urgent++;
            }
            if (report.holder(now, claimMillis).isPresent()) {
                progress++;
            }
        }
        return new Counts(open, urgent, progress, open - progress);
    }

    public static boolean urgent(Report report, int urgentFrom) {
        return report.reporters() >= urgentFrom;
    }

    private static Predicate<Report> matches(Filter filter, String viewer, long now, long claimMillis) {
        return switch (filter) {
            case ALL -> report -> true;
            case FREE -> report -> report.holder(now, claimMillis).map(viewer::equalsIgnoreCase).orElse(true);
            case MINE -> report -> report.heldBy(viewer, now, claimMillis);
            case CHAT -> report -> !report.chat().isEmpty();
        };
    }

    private static Comparator<Report> order(Sort sort) {
        return switch (sort) {
            case URGENT -> Comparator.comparingInt(Report::reporters).reversed()
                    .thenComparingLong(Report::createdAt);
            case NEWEST -> Comparator.comparingLong(Report::lastReportAt).reversed();
            case OLDEST -> Comparator.comparingLong(Report::createdAt);
        };
    }
}
