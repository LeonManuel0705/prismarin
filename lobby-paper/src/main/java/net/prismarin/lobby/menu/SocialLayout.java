package net.prismarin.lobby.menu;

import java.util.ArrayList;
import java.util.List;

public final class SocialLayout {

    public static final int ROWS = 6;
    public static final int PER_PAGE = 28;
    public static final int INFO = 4;
    public static final int BACK = 45;
    public static final int REQUESTS = 47;
    public static final int PREVIOUS = 48;
    public static final int PARTY = 49;
    public static final int NEXT = 50;
    public static final int SETTINGS = 51;
    public static final int CLOSE = 53;
    public static final int EMPTY = 22;

    private SocialLayout() {
        throw new AssertionError();
    }

    public static List<Integer> contentSlots(int count) {
        return slots(1, 4, count);
    }

    public static List<Integer> slots(int firstRow, int rows, int count) {
        List<Integer> slots = new ArrayList<>();
        for (int row = firstRow; row < firstRow + rows && slots.size() < count; row++) {
            for (int column = 1; column <= 7 && slots.size() < count; column++) {
                slots.add(row * 9 + column);
            }
        }
        return List.copyOf(slots);
    }

    public static boolean hasPrevious(int page) {
        return page > 0;
    }

    public static boolean hasNext(int page, int pages) {
        return page + 1 < pages;
    }

    public static int badge(int count) {
        return Math.max(1, Math.min(64, count));
    }
}
