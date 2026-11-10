package net.prismarin.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class NetworkLayoutTest {

    @Test
    void centersFewServersWithGaps() {
        assertEquals(List.of(13), NetworkLayout.slots(1));
        assertEquals(List.of(11, 13, 15), NetworkLayout.slots(3));
        assertEquals(List.of(10, 12, 14, 16), NetworkLayout.slots(4));
    }

    @Test
    void wrapsIntoFurtherRows() {
        assertEquals(List.of(10, 12, 14, 16, 22), NetworkLayout.slots(5));
        assertEquals(3, NetworkLayout.rows(1));
        assertEquals(4, NetworkLayout.rows(5));
        assertEquals(6, NetworkLayout.rows(16));
    }

    @Test
    void paginatesBeyondSixteenServers() {
        assertEquals(1, NetworkLayout.pages(0));
        assertEquals(1, NetworkLayout.pages(16));
        assertEquals(2, NetworkLayout.pages(17));
        assertThrows(IllegalArgumentException.class, () -> NetworkLayout.slots(17));
    }

    @Test
    void showsAtLeastOneSegmentForAnyPlayer() {
        assertEquals(0, NetworkLayout.filled(0, 50, 10));
        assertEquals(1, NetworkLayout.filled(1, 50, 10));
        assertEquals(5, NetworkLayout.filled(25, 50, 10));
        assertEquals(10, NetworkLayout.filled(80, 50, 10));
        assertEquals(0, NetworkLayout.filled(3, 0, 10));
    }
}
