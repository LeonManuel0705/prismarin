package net.prismarin.core.tablist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TeamSortKeysTest {

    @Test
    void zeroWeightUsesBaseOrder() {
        assertEquals("psm1000Steve", TeamSortKeys.sortKey(0, "Steve"));
    }

    @Test
    void weightIsSubtractedFromBaseOrder() {
        assertEquals("psm0900Admin", TeamSortKeys.sortKey(100, "Admin"));
    }

    @Test
    void orderIsZeroPaddedToFourDigits() {
        assertEquals("psm0001Steve", TeamSortKeys.sortKey(999, "Steve"));
    }

    @Test
    void higherWeightSortsLexicographicallyFirst() {
        String admin = TeamSortKeys.sortKey(100, "Admin");
        String player = TeamSortKeys.sortKey(0, "Steve");
        assertTrue(admin.compareTo(player) < 0);
    }

    @Test
    void weightAboveBaseClampsToZero() {
        assertEquals("psm0000Steve", TeamSortKeys.sortKey(1500, "Steve"));
    }

    @Test
    void negativeWeightClampsToUpperBound() {
        assertEquals("psm9999Steve", TeamSortKeys.sortKey(-20000, "Steve"));
    }

    @Test
    void longNamesWithSharedPrefixProduceDistinctKeys() {
        String first = TeamSortKeys.sortKey(0, "ABCDEFGHIJKLMNOP");
        String second = TeamSortKeys.sortKey(0, "ABCDEFGHIJKLMNOQ");
        assertNotEquals(first, second);
        assertEquals("psm1000ABCDEFGHIJKLMNOP", first);
    }

    @Test
    void shortNamesAreNotPadded() {
        assertEquals("psm1000Al", TeamSortKeys.sortKey(0, "Al"));
    }

    @Test
    void generatedKeysAreRecognizedAsManaged() {
        assertTrue(TeamSortKeys.isManaged(TeamSortKeys.sortKey(0, "Steve")));
        assertTrue(TeamSortKeys.isManaged(TeamSortKeys.sortKey(-20000, "ABCDEFGHIJKLMNOP")));
    }

    @Test
    void foreignTeamsAreNotManaged() {
        assertFalse(TeamSortKeys.isManaged("1000Steve"));
        assertFalse(TeamSortKeys.isManaged("collisionTeam"));
        assertFalse(TeamSortKeys.isManaged("psmXXXXSteve"));
        assertFalse(TeamSortKeys.isManaged("psm"));
    }
}
