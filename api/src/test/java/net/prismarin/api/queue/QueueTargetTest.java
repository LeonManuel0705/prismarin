package net.prismarin.api.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QueueTargetTest {

    @Test
    void namesAreNormalisedAndIdsRoundTrip() {
        QueueTarget group = QueueTarget.group(" BedWars ");
        assertEquals("bedwars", group.name());
        assertEquals("group:bedwars", group.id());
        assertTrue(group.isGroup());
        assertFalse(group.isServer());
        assertEquals(Optional.of(group), QueueTarget.parse("group:bedwars"));
        assertEquals(Optional.of(QueueTarget.server("bw-1")), QueueTarget.parse("SERVER:BW-1"));
        assertEquals(QueueTarget.server("BW-1"), QueueTarget.server("bw-1"));
    }

    @Test
    void brokenIdsAreRejected() {
        for (String broken : new String[]{null, "", "bedwars", "group:", ":bedwars", "planet:mars", "group: "}) {
            assertEquals(Optional.empty(), QueueTarget.parse(broken), String.valueOf(broken));
        }
        assertThrows(IllegalArgumentException.class, () -> QueueTarget.group(" "));
    }

    @Test
    void resultsAndPositionsDescribeTheirState() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        QueuePosition position = new QueuePosition(QueueTarget.group("bedwars"), owner, List.of(member), 1, 4, 6, 0,
                false, 1L, true);
        assertTrue(position.first());
        assertEquals(2, position.unitSize());
        assertTrue(position.includes(member));
        assertTrue(QueueResult.waiting(QueueStatus.ALREADY_QUEUED, position).waiting());
        assertTrue(QueueResult.sent("bw-1").sent());
        assertFalse(QueueResult.of(QueueStatus.NO_SERVER).waiting());
        for (QueueStatus status : QueueStatus.values()) {
            assertEquals(Optional.of(status), QueueStatus.byId(status.id()));
        }
        assertEquals("not-leader", QueueStatus.NOT_LEADER.id());
    }
}
