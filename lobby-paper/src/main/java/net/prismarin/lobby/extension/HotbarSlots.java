package net.prismarin.lobby.extension;

public final class HotbarSlots {

    public static final int FIRST = 0;
    public static final int LAST = 8;

    private HotbarSlots() {
        throw new AssertionError();
    }

    public static boolean valid(int slot) {
        return slot >= FIRST && slot <= LAST;
    }

    public static String problem(int slot) {
        return valid(slot) ? null : "slot " + slot + " is not a hotbar slot (" + FIRST + " to " + LAST + ")";
    }
}
