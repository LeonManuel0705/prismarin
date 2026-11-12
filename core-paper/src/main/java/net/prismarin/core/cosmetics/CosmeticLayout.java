package net.prismarin.core.cosmetics;

import java.util.ArrayList;
import java.util.List;

public final class CosmeticLayout {

    public static final int COLUMNS = 9;
    public static final int ITEM_COLUMNS = 7;

    private static final List<List<Integer>> ROWS = List.of(
            List.of(),
            List.of(4),
            List.of(3, 5),
            List.of(2, 4, 6),
            List.of(1, 3, 5, 7),
            List.of(2, 3, 4, 5, 6),
            List.of(1, 2, 3, 5, 6, 7),
            List.of(1, 2, 3, 4, 5, 6, 7),
            List.of(0, 1, 2, 3, 5, 6, 7, 8),
            List.of(0, 1, 2, 3, 4, 5, 6, 7, 8));

    private CosmeticLayout() {
        throw new AssertionError();
    }

    public static List<Integer> columns(int count) {
        return ROWS.get(Math.clamp(count, 0, COLUMNS));
    }

    public static int rowsFor(int count) {
        return Math.max(1, (count + ITEM_COLUMNS - 1) / ITEM_COLUMNS);
    }

    public static List<Integer> categorySlots(int count, int firstRow) {
        List<Integer> slots = new ArrayList<>();
        int left = count;
        int row = firstRow;
        while (left > 0) {
            int inRow = Math.min(left, ITEM_COLUMNS);
            for (int column : columns(inRow)) {
                slots.add(row * COLUMNS + column);
            }
            left -= inRow;
            row += 2;
        }
        return List.copyOf(slots);
    }

    public static List<Integer> itemSlots(int rows) {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= rows; row++) {
            for (int column = 1; column <= ITEM_COLUMNS; column++) {
                slots.add(row * COLUMNS + column);
            }
        }
        return List.copyOf(slots);
    }

    public static int pages(int items, int perPage) {
        return items <= 0 ? 1 : (items + perPage - 1) / perPage;
    }
}
