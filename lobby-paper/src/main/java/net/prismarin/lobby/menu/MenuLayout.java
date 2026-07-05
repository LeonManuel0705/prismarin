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
}
