package net.prismarin.velocity.social;

import java.util.List;

public record Page<T>(List<T> entries, int page, int pages, int total) {

    public Page {
        entries = List.copyOf(entries);
    }

    public static <T> Page<T> of(List<T> all, int requested, int size) {
        int perPage = Math.max(1, size);
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        int page = Math.max(0, Math.min(requested, pages - 1));
        int from = Math.min(all.size(), page * perPage);
        int to = Math.min(all.size(), from + perPage);
        return new Page<>(all.subList(from, to), page, pages, all.size());
    }
}
