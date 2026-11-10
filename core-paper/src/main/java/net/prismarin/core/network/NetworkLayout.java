package net.prismarin.core.network;

import java.util.ArrayList;
import java.util.List;

public final class NetworkLayout {

    public static final int COLUMNS = 9;
    public static final int PER_ROW = 4;
    public static final int MAX_CONTENT_ROWS = 4;
    public static final int PER_PAGE = PER_ROW * MAX_CONTENT_ROWS;

    private NetworkLayout() {
        throw new AssertionError();
    }

    public static int pages(int servers) {
        return Math.max(1, (servers + PER_PAGE - 1) / PER_PAGE);
    }

    public static int contentRows(int serversOnPage) {
        return Math.max(1, (serversOnPage + PER_ROW - 1) / PER_ROW);
    }

    public static int rows(int serversOnPage) {
        return contentRows(serversOnPage) + 2;
    }

    public static List<Integer> slots(int serversOnPage) {
        if (serversOnPage < 0 || serversOnPage > PER_PAGE) {
            throw new IllegalArgumentException("between 0 and " + PER_PAGE + " servers fit on a page");
        }
        List<Integer> slots = new ArrayList<>(serversOnPage);
        int remaining = serversOnPage;
        int row = 1;
        while (remaining > 0) {
            int inRow = Math.min(PER_ROW, remaining);
            int start = 4 - (inRow - 1);
            for (int i = 0; i < inRow; i++) {
                slots.add(row * COLUMNS + start + i * 2);
            }
            remaining -= inRow;
            row++;
        }
        return List.copyOf(slots);
    }

    public static int filled(int value, int max, int segments) {
        if (max <= 0) {
            return 0;
        }
        int filled = (int) Math.round(Math.min(1D, (double) value / max) * segments);
        return value > 0 && filled == 0 ? 1 : filled;
    }
}
