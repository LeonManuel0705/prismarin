package net.prismarin.lobby.menu;

import java.util.ArrayList;
import java.util.List;

public final class MenuLayout {

    private static final int COLUMNS = 9;
    private static final int CENTER_COLUMN = 4;
    private static final int MAX_ROW = 5;

    private MenuLayout() {
        throw new AssertionError();
    }

    public static List<Integer> centeredRowSlots(int row, int count) {
        if (row < 0 || row > MAX_ROW) {
            throw new IllegalArgumentException("row must be between 0 and " + MAX_ROW);
        }
        if (count < 1 || count > COLUMNS) {
            throw new IllegalArgumentException("count must be between 1 and " + COLUMNS);
        }
        List<Integer> slots = new ArrayList<>(count);
        if (count * 2 - 1 <= COLUMNS) {
            int start = CENTER_COLUMN - (count - 1);
            for (int i = 0; i < count; i++) {
                slots.add(row * COLUMNS + start + i * 2);
            }
        } else {
            int start = (COLUMNS - count) / 2;
            for (int i = 0; i < count; i++) {
                slots.add(row * COLUMNS + start + i);
            }
        }
        return List.copyOf(slots);
    }

    public static int rowsFor(int count, int perRow) {
        if (perRow < 1 || perRow > COLUMNS) {
            throw new IllegalArgumentException("perRow must be between 1 and " + COLUMNS);
        }
        return count <= 0 ? 1 : (count + perRow - 1) / perRow;
    }

    public static List<Integer> centeredSlots(int firstRow, int count, int perRow) {
        if (count < 1) {
            return List.of();
        }
        int rows = rowsFor(count, perRow);
        if (firstRow < 0 || firstRow + rows - 1 > MAX_ROW) {
            throw new IllegalArgumentException(count + " entries do not fit below row " + firstRow);
        }
        int base = count / rows;
        int extra = count % rows;
        List<Integer> slots = new ArrayList<>(count);
        for (int row = 0; row < rows; row++) {
            slots.addAll(centeredRowSlots(firstRow + row, base + (row < extra ? 1 : 0)));
        }
        return List.copyOf(slots);
    }
}
