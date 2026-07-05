package net.prismarin.core.tablist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TeamSortKeysTest {

    @Test
    void zeroWeightUsesBaseOrder() {
        assertEquals("1000Steve", TeamSortKeys.sortKey(0, "Steve"));
    }

    @Test
    void weightIsSubtractedFromBaseOrder() {
        assertEquals("0900Admin", TeamSortKeys.sortKey(100, "Admin"));
    }

    @Test
    void orderIsZeroPaddedToFourDigits() {
        assertEquals("0001Steve", TeamSortKeys.sortKey(999, "Steve"));
    }

    @Test
    void higherWeightSortsLexicographicallyFirst() {
        String admin = TeamSortKeys.sortKey(100, "Admin");
        String player = TeamSortKeys.sortKey(0, "Steve");
        assertTrue(admin.compareTo(player) < 0);
    }

    @Test
    void weightAboveBaseClampsToZero() {
        assertEquals("0000Steve", TeamSortKeys.sortKey(1500, "Steve"));
    }

    @Test
    void negativeWeightClampsToUpperBound() {
        assertEquals("9999Steve", TeamSortKeys.sortKey(-20000, "Steve"));
    }

    @Test
    void keyIsTruncatedToSixteenCharacters() {
        String key = TeamSortKeys.sortKey(0, "ABCDEFGHIJKLMNOP");
        assertEquals(16, key.length());
        assertEquals("1000ABCDEFGHIJKL", key);
    }

    @Test
    void shortNamesAreNotPadded() {
        assertEquals("1000Al", TeamSortKeys.sortKey(0, "Al"));
    }
}
