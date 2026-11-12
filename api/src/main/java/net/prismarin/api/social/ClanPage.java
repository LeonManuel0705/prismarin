package net.prismarin.api.social;

import java.util.List;

public record ClanPage(List<Clan> clans, int page, int pages, int total) {

    public static final ClanPage EMPTY = new ClanPage(List.of(), 0, 1, 0);

    public ClanPage {
        clans = List.copyOf(clans);
        page = Math.max(0, page);
        pages = Math.max(1, pages);
        total = Math.max(0, total);
    }

    public boolean hasNext() {
        return page + 1 < pages;
    }

    public static ClanPage of(List<Clan> all, int page, int size) {
        int pageSize = Math.max(1, size);
        int pages = Math.max(1, (all.size() + pageSize - 1) / pageSize);
        int current = Math.max(0, Math.min(page, pages - 1));
        int from = current * pageSize;
        return new ClanPage(all.subList(Math.min(from, all.size()), Math.min(from + pageSize, all.size())), current,
                pages, all.size());
    }
}
