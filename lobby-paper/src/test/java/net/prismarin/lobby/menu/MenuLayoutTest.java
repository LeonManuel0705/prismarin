package net.prismarin.lobby.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class MenuLayoutTest {

    @Test
    void centersSingleEntry() {
        assertEquals(List.of(13), MenuLayout.centeredRowSlots(1, 1));
    }

    @Test
    void spreadsSmallCountsWithGaps() {
        assertEquals(List.of(12, 14), MenuLayout.centeredRowSlots(1, 2));
        assertEquals(List.of(11, 13, 15), MenuLayout.centeredRowSlots(1, 3));
        assertEquals(List.of(10, 12, 14, 16), MenuLayout.centeredRowSlots(1, 4));
        assertEquals(List.of(9, 11, 13, 15, 17), MenuLayout.centeredRowSlots(1, 5));
    }

    @Test
    void packsLargeCountsContiguously() {
        assertEquals(List.of(10, 11, 12, 13, 14, 15), MenuLayout.centeredRowSlots(1, 6));
        assertEquals(List.of(9, 10, 11, 12, 13, 14, 15, 16, 17), MenuLayout.centeredRowSlots(1, 9));
    }

    @Test
    void offsetsSlotsByRow() {
        assertEquals(List.of(2, 4, 6), MenuLayout.centeredRowSlots(0, 3));
        assertEquals(List.of(20, 22, 24), MenuLayout.centeredRowSlots(2, 3));
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> MenuLayout.centeredRowSlots(1, 0));
        assertThrows(IllegalArgumentException.class, () -> MenuLayout.centeredRowSlots(1, 10));
        assertThrows(IllegalArgumentException.class, () -> MenuLayout.centeredRowSlots(-1, 3));
        assertThrows(IllegalArgumentException.class, () -> MenuLayout.centeredRowSlots(6, 3));
    }
}
