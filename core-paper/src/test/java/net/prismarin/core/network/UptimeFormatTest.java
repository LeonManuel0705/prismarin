package net.prismarin.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UptimeFormatTest {

    @Test
    void uptimeIsFormattedCompactly() {
        assertEquals("0m", UptimeFormat.compact(30));
        assertEquals("5m", UptimeFormat.compact(300));
        assertEquals("2h 3m", UptimeFormat.compact(7380));
        assertEquals("3d 4h", UptimeFormat.compact(3 * 86_400 + 4 * 3_600 + 59));
    }
}
