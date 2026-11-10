package net.prismarin.shared.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record Report(String id, UUID reporter, String reporterName, UUID target, String targetName, String reason,
                     String server, long createdAt, boolean open, String closedBy, long closedAt,
                     List<String> chat, List<Supporter> supporters, String claimedBy, String context,
                     String verdict, String shownName, long claimedAt) {

    public static final String VALID = "valid";
    public static final String INVALID = "invalid";
    public static final String PUNISHED = "punished";

    public record Supporter(UUID uuid, String name, String reason, long at) {
    }

    public Report {
        chat = chat == null ? List.of() : List.copyOf(chat);
        supporters = supporters == null ? List.of() : List.copyOf(supporters);
        claimedBy = claimedBy == null ? "" : claimedBy;
        context = context == null ? "" : context;
        verdict = verdict == null ? "" : verdict;
        closedBy = closedBy == null ? "" : closedBy;
        shownName = shownName == null || shownName.isBlank() ? targetName : shownName;
    }

    public Report(String id, UUID reporter, String reporterName, UUID target, String targetName, String reason,
                  String server, long createdAt, boolean open, String closedBy, long closedAt, List<String> chat) {
        this(id, reporter, reporterName, target, targetName, reason, server, createdAt, open, closedBy, closedAt,
                chat, List.of(), "", "", "", targetName, 0L);
    }

    public int reporters() {
        return 1 + supporters.size();
    }

    public boolean reportedBy(UUID uuid) {
        return reporter.equals(uuid) || supporters.stream().anyMatch(supporter -> supporter.uuid().equals(uuid));
    }

    public List<UUID> reporterIds() {
        List<UUID> ids = new ArrayList<>();
        ids.add(reporter);
        supporters.forEach(supporter -> ids.add(supporter.uuid()));
        return List.copyOf(ids);
    }

    public List<String> reasons() {
        List<String> reasons = new ArrayList<>();
        reasons.add(reason);
        supporters.forEach(supporter -> {
            if (supporter.reason() != null && reasons.stream().noneMatch(supporter.reason()::equalsIgnoreCase)) {
                reasons.add(supporter.reason());
            }
        });
        return List.copyOf(reasons);
    }

    public long lastReportAt() {
        return supporters.stream().mapToLong(Supporter::at).max().orElse(createdAt);
    }

    public Optional<String> holder(long now, long timeoutMillis) {
        if (!open || claimedBy.isEmpty()) {
            return Optional.empty();
        }
        if (timeoutMillis > 0L && now - claimedAt >= timeoutMillis) {
            return Optional.empty();
        }
        return Optional.of(claimedBy);
    }

    public boolean heldBy(String staff, long now, long timeoutMillis) {
        return holder(now, timeoutMillis).map(staff::equalsIgnoreCase).orElse(false);
    }

    public Report support(Supporter supporter, List<String> moreChat) {
        List<Supporter> all = new ArrayList<>(supporters);
        all.add(supporter);
        List<String> mergedChat = moreChat.isEmpty() ? chat : moreChat;
        return new Report(id, reporter, reporterName, target, targetName, reason, server, createdAt, open, closedBy,
                closedAt, mergedChat, all, claimedBy, context, verdict, shownName, claimedAt);
    }

    public Report claim(String by) {
        return claim(by, claimedAt);
    }

    public Report claim(String by, long at) {
        return new Report(id, reporter, reporterName, target, targetName, reason, server, createdAt, open, closedBy,
                closedAt, chat, supporters, by, context, verdict, shownName, at);
    }

    public Report close(String by, long at) {
        return close(by, at, VALID);
    }

    public Report close(String by, long at, String verdict) {
        return new Report(id, reporter, reporterName, target, targetName, reason, server, createdAt, false, by, at,
                chat, supporters, claimedBy, context, verdict, shownName, claimedAt);
    }
}
