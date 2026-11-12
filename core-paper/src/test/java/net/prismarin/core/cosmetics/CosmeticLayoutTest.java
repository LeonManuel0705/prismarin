package net.prismarin.core.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class CosmeticLayoutTest {

    @Test
    void everyRowIsCenteredAndEvenRowsKeepTheMiddleFree() {
        for (int count = 1; count <= CosmeticLayout.COLUMNS; count++) {
            List<Integer> columns = CosmeticLayout.columns(count);
            assertEquals(count, columns.size());
            for (int index = 0; index < count; index++) {
                assertEquals(8 - columns.get(index), columns.get(count - 1 - index), "count " + count);
            }
        }
        assertFalse(CosmeticLayout.columns(6).contains(4));
        assertFalse(CosmeticLayout.columns(8).contains(4));
        assertEquals(List.of(1, 2, 3, 5, 6, 7), CosmeticLayout.columns(6));
    }

    @Test
    void sixCategoriesFillTheMiddleRowAroundTheCentre() {
        assertEquals(List.of(19, 20, 21, 23, 24, 25), CosmeticLayout.categorySlots(6, 2));
        assertEquals(List.of(10, 11, 12, 13, 14, 15, 16, 30, 32), CosmeticLayout.categorySlots(9, 1));
    }

    @Test
    void itemsUseTheInnerSevenColumns() {
        List<Integer> slots = CosmeticLayout.itemSlots(4);

        assertEquals(28, slots.size());
        assertEquals(10, slots.getFirst());
        assertEquals(43, slots.getLast());
        assertFalse(slots.contains(17));
        assertFalse(slots.contains(18));
        assertEquals(1, CosmeticLayout.pages(0, 28));
        assertEquals(1, CosmeticLayout.pages(28, 28));
        assertEquals(2, CosmeticLayout.pages(29, 28));
    }
}
