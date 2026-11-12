package net.prismarin.velocity.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.prismarin.api.queue.QueueLeftEvent;
import net.prismarin.api.queue.QueuePosition;
import net.prismarin.api.queue.QueueStatus;
import net.prismarin.api.queue.QueueTarget;
import org.junit.jupiter.api.Test;

class QueueBookTest {

    private static final QueueTarget BEDWARS = QueueTarget.group("bedwars");
    private static final QueueTarget SKYWARS = QueueTarget.group("skywars");
    private static final QueueTarget BW_1 = QueueTarget.server("bw-1");

    private final QueueBook book = new QueueBook();
    private final UUID anna = UUID.randomUUID();
    private final UUID ben = UUID.randomUUID();
    private final UUID cara = UUID.randomUUID();
    private final UUID dana = UUID.randomUUID();
    private final UUID emil = UUID.randomUUID();

    private static Function<QueueTarget, List<QueueBook.Slot>> slots(Map<QueueTarget, List<QueueBook.Slot>> slots) {
        return target -> slots.getOrDefault(target, List.of());
    }

    private static QueueBook.Slot slot(String server, int free) {
        return new QueueBook.Slot(server, free, 1, 0);
    }

    private List<UUID> owners(List<QueueBook.Assignment> assignments) {
        return assignments.stream().map(QueueBook.Assignment::owner).toList();
    }

    @Test
    void samePriorityKeepsFirstComeFirstServed() {
        book.join(BEDWARS, anna, List.of(), 0, true, 100L);
        book.join(BEDWARS, ben, List.of(), 0, true, 200L);
        book.join(BEDWARS, cara, List.of(), 0, true, 200L);
        assertEquals(1, book.position(anna).orElseThrow().position());
        assertEquals(2, book.position(ben).orElseThrow().position());
        assertEquals(3, book.position(cara).orElseThrow().position());
        List<QueueBook.Assignment> sent = book.plan(slots(Map.of(BEDWARS, List.of(slot("bw-1", 2)))),
                target -> true, 1_000L);
        assertEquals(List.of(anna, ben), owners(sent));
        assertEquals(1, book.position(cara).orElseThrow().position());
    }

    @Test
    void higherPriorityGoesFirstAndTiesStayInOrder() {
        book.join(BEDWARS, anna, List.of(), 0, true, 100L);
        book.join(BEDWARS, ben, List.of(), 10, true, 200L);
        book.join(BEDWARS, cara, List.of(), 20, true, 300L);
        book.join(BEDWARS, dana, List.of(), 10, true, 400L);
        assertEquals(List.of(cara, ben, dana, anna), book.entries(BEDWARS, UUID::toString).stream()
                .map(entry -> entry.owner()).toList());
        assertEquals(4, book.position(anna).orElseThrow().position());
        List<QueueBook.Assignment> sent = book.plan(slots(Map.of(BEDWARS, List.of(slot("bw-1", 3)))),
                target -> true, 1_000L);
        assertEquals(List.of(cara, ben, dana), owners(sent));
    }

    @Test
    void partyWaitsUntilEveryoneFitsAndHoldsThePlayersBehind() {
        book.join(BEDWARS, anna, List.of(ben, cara), 0, true, 100L);
        book.join(BEDWARS, dana, List.of(), 0, true, 200L);
        QueuePosition position = book.position(ben).orElseThrow();
        assertEquals(anna, position.owner());
        assertEquals(3, position.unitSize());
        assertEquals(4, position.players());
        assertTrue(book.plan(slots(Map.of(BEDWARS, List.of(slot("bw-1", 2)))), target -> true, 1_000L).isEmpty());
        List<QueueBook.Assignment> sent = book.plan(slots(Map.of(BEDWARS, List.of(slot("bw-1", 3)))),
                target -> true, 2_000L);
        assertEquals(1, sent.size());
        assertEquals(List.of(anna, ben, cara), sent.getFirst().players());
        assertEquals(3, book.inFlight("BW-1"));
    }

    @Test
    void withoutHoldingSmallerUnitsTakeFreeSlotsBehindAParty() {
        book.join(BEDWARS, anna, List.of(ben, cara), 0, true, 100L);
        book.join(BEDWARS, dana, List.of(), 0, true, 200L);
        List<QueueBook.Assignment> sent = book.plan(slots(Map.of(BEDWARS, List.of(slot("bw-1", 2)))),
                target -> false, 1_000L);
        assertEquals(List.of(dana), owners(sent));
        assertEquals(1, book.position(anna).orElseThrow().position());
    }

    @Test
    void partyChoosesTheServerWhereEveryoneFits() {
        book.join(BEDWARS, anna, List.of(ben, cara), 0, true, 100L);
        List<QueueBook.Assignment> sent = book.plan(slots(Map.of(BEDWARS, List.of(
                new QueueBook.Slot("bw-1", 2, 0, 10), new QueueBook.Slot("bw-2", 5, 1, 3)))), target -> true, 1L);
        assertEquals("bw-2", sent.getFirst().server());
    }

    @Test
    void startingServersAndFullerServersArePreferred() {
        book.join(BEDWARS, anna, List.of(), 0, true, 100L);
        book.join(BEDWARS, ben, List.of(), 0, true, 200L);
        List<QueueBook.Assignment> sent = book.plan(slots(Map.of(BEDWARS, List.of(
                new QueueBook.Slot("bw-1", 4, 1, 2), new QueueBook.Slot("bw-2", 4, 1, 6),
                new QueueBook.Slot("bw-3", 1, 0, 1)))), target -> true, 1L);
        assertEquals("bw-3", sent.get(0).server());
        assertEquals("bw-2", sent.get(1).server());
    }

    @Test
    void maxPlayersAndFlightsLimitHowManyAreSent() {
        for (UUID player : List.of(anna, ben, cara, dana)) {
            book.join(BW_1, player, List.of(), 0, true, 100L);
        }
        assertEquals(2, book.plan(slots(Map.of(BW_1, List.of(slot("bw-1", 2)))), target -> true, 1L).size());
        assertTrue(book.plan(slots(Map.of(BW_1, List.of(slot("bw-1", 2)))), target -> true, 2L).isEmpty());
        book.landed(anna);
        book.landed(ben);
        assertEquals(1, book.plan(slots(Map.of(BW_1, List.of(slot("bw-1", 1)))), target -> true, 3L).size());
        assertEquals(1, book.plan(slots(Map.of(BW_1, List.of(slot("bw-1", QueueBook.Slot.UNLIMITED)))),
                target -> true, 4L).size());
        assertTrue(book.plan(slots(Map.of(BW_1, List.of())), target -> true, 5L).isEmpty());
    }

    @Test
    void queuesForTheSameServerShareItsSlotsInJoinOrder() {
        book.join(BW_1, anna, List.of(), 0, true, 300L);
        book.join(BEDWARS, ben, List.of(), 0, true, 100L);
        book.join(BEDWARS, cara, List.of(), 0, true, 400L);
        List<QueueBook.Slot> shared = List.of(slot("bw-1", 2));
        List<QueueBook.Assignment> sent = book.plan(slots(Map.of(BW_1, shared, BEDWARS, shared)), target -> true, 1L);
        assertEquals(List.of(ben, anna), owners(sent));
        assertEquals(1, book.size(BEDWARS));
    }

    @Test
    void disconnectedOwnerKeepsThePlaceDuringTheGraceTime() {
        book.join(BEDWARS, anna, List.of(), 0, true, 100L);
        book.join(BEDWARS, ben, List.of(), 0, true, 200L);
        assertTrue(book.offline(anna, 1_000L).isEmpty());
        assertTrue(book.away(anna));
        assertFalse(book.position(anna).orElseThrow().online());
        List<QueueBook.Assignment> sent = book.plan(slots(Map.of(BEDWARS, List.of(slot("bw-1", 1)))),
                target -> true, 2_000L);
        assertEquals(List.of(ben), owners(sent));
        assertTrue(book.expire(30_000L, 60_000L).isEmpty());
        List<QueuePosition> back = book.online(anna);
        assertEquals(1, back.getFirst().position());
        assertTrue(back.getFirst().online());
        book.landed(ben);
        assertEquals(List.of(anna), owners(book.plan(slots(Map.of(BEDWARS, List.of(slot("bw-1", 1)))),
                target -> true, 31_000L)));
    }

    @Test
    void graceTimeRunsOutAndRemovesTheOwnerWithTheParty() {
        book.join(BEDWARS, anna, List.of(ben), 0, true, 100L);
        book.offline(anna, 1_000L);
        assertTrue(book.expire(60_999L, 60_000L).isEmpty());
        List<QueueBook.Removal> removed = book.expire(61_000L, 60_000L);
        assertEquals(1, removed.size());
        assertEquals(QueueLeftEvent.Reason.DISCONNECTED, removed.getFirst().reason());
        assertEquals(List.of(ben), removed.getFirst().party());
        assertFalse(book.queued(anna));
        assertFalse(book.queued(ben));
        assertFalse(book.away(anna));
    }

    @Test
    void disconnectedMemberLeavesThePartyUnitAtOnce() {
        book.join(BEDWARS, anna, List.of(ben, cara), 0, true, 100L);
        List<QueueBook.Removal> removed = book.offline(ben, 1_000L);
        assertEquals(1, removed.size());
        assertEquals(ben, removed.getFirst().player());
        assertEquals(List.of(cara), book.position(anna).orElseThrow().party());
        assertFalse(book.away(ben));
    }

    @Test
    void pausedQueueSendsNobodyUntilItIsResumed() {
        book.join(BEDWARS, anna, List.of(), 0, true, 100L);
        assertTrue(book.pause(BEDWARS));
        assertFalse(book.pause(BEDWARS));
        assertTrue(book.paused(BEDWARS));
        assertTrue(book.position(anna).orElseThrow().paused());
        assertTrue(book.plan(slots(Map.of(BEDWARS, List.of(slot("bw-1", 5)))), target -> true, 1L).isEmpty());
        assertTrue(book.holds(BEDWARS));
        assertTrue(book.resume(BEDWARS));
        assertFalse(book.resume(BEDWARS));
        assertEquals(List.of(anna), owners(book.plan(slots(Map.of(BEDWARS, List.of(slot("bw-1", 5)))),
                target -> true, 2L)));
    }

    @Test
    void emptyQueuesCanBePausedAndHoldNewConnections() {
        assertFalse(book.holds(BW_1));
        book.pause(BW_1);
        assertTrue(book.holds(BW_1));
        assertEquals(List.of(BW_1), book.targets());
        book.resume(BW_1);
        assertTrue(book.targets().isEmpty());
    }

    @Test
    void switchingServersLeavesTheQueue() {
        book.join(BEDWARS, anna, List.of(ben), 0, true, 100L);
        book.join(SKYWARS, cara, List.of(), 0, false, 100L);
        List<QueueBook.Removal> member = book.leave(ben, null, QueueLeftEvent.Reason.SWITCHED_SERVER);
        assertEquals(List.of(ben), member.getFirst().players());
        assertTrue(book.queued(anna));
        List<QueueBook.Removal> owner = book.leave(anna, null, QueueLeftEvent.Reason.SWITCHED_SERVER);
        assertEquals(QueueLeftEvent.Reason.SWITCHED_SERVER, owner.getFirst().reason());
        assertFalse(book.queued(anna));
        assertTrue(book.queued(cara));
    }

    @Test
    void oneQueuePerPlayerMovesThePlayerToTheNewQueue() {
        book.join(BEDWARS, anna, List.of(), 0, true, 100L);
        QueueBook.Joined joined = book.join(SKYWARS, anna, List.of(), 0, true, 200L);
        assertEquals(QueueStatus.QUEUED, joined.status());
        assertEquals(1, joined.removed().size());
        assertEquals(BEDWARS, joined.removed().getFirst().target());
        assertEquals(QueueLeftEvent.Reason.OTHER_QUEUE, joined.removed().getFirst().reason());
        assertEquals(List.of(SKYWARS), book.positions(anna).stream().map(QueuePosition::target).toList());
    }

    @Test
    void severalQueuesAreAllowedWhenConfiguredAndTheFirstSendWins() {
        book.join(BEDWARS, anna, List.of(), 0, false, 100L);
        book.join(SKYWARS, anna, List.of(), 0, false, 200L);
        assertEquals(2, book.positions(anna).size());
        List<QueueBook.Assignment> sent = book.plan(slots(Map.of(SKYWARS, List.of(slot("sw-1", 1)))),
                target -> true, 1L);
        assertEquals(SKYWARS, sent.getFirst().target());
        List<QueueBook.Removal> removed = book.landed(anna);
        assertEquals(1, removed.size());
        assertEquals(QueueLeftEvent.Reason.SENT_ELSEWHERE, removed.getFirst().reason());
        assertFalse(book.queued(anna));
    }

    @Test
    void joiningTheSameQueueTwiceKeepsThePlace() {
        book.join(BEDWARS, anna, List.of(), 0, true, 100L);
        book.join(BEDWARS, ben, List.of(), 0, true, 200L);
        QueueBook.Joined again = book.join(BEDWARS, ben, List.of(), 50, true, 300L);
        assertEquals(QueueStatus.ALREADY_QUEUED, again.status());
        assertEquals(2, again.position().position());
        QueueBook.Joined member = book.join(QueueTarget.group("bedwars"), cara, List.of(), 0, true, 400L);
        assertEquals(QueueStatus.QUEUED, member.status());
    }

    @Test
    void partyJoinPullsMembersOutOfTheirOwnQueues() {
        book.join(BEDWARS, ben, List.of(), 0, true, 100L);
        book.join(SKYWARS, cara, List.of(), 0, true, 100L);
        QueueBook.Joined joined = book.join(BEDWARS, anna, List.of(ben, cara, anna), 0, true, 200L);
        assertEquals(2, joined.removed().size());
        assertEquals(List.of(ben, cara), book.position(anna).orElseThrow().party());
        assertEquals(1, book.size(BEDWARS));
        assertEquals(0, book.size(SKYWARS));
    }

    @Test
    void partyMembersWhoLeftThePartyAreDropped() {
        book.join(BEDWARS, anna, List.of(ben, cara), 0, true, 100L);
        List<QueueBook.Removal> removed = book.prune((owner, member) -> !member.equals(cara),
                QueueLeftEvent.Reason.PARTY_CHANGED);
        assertEquals(List.of(cara), removed.stream().map(QueueBook.Removal::player).toList());
        assertEquals(List.of(ben), book.position(anna).orElseThrow().party());
    }

    @Test
    void failedSendGoesBackToTheSamePlaceAndWaits() {
        book.join(BEDWARS, anna, List.of(), 0, true, 100L);
        book.join(BEDWARS, ben, List.of(), 0, true, 200L);
        book.plan(slots(Map.of(BEDWARS, List.of(slot("bw-1", 1)))), target -> true, 1_000L);
        assertTrue(book.flightOf(anna).isPresent());
        assertEquals(Optional.empty(), book.failed(anna, 1_000L, 5_000L, 3));
        assertEquals(1, book.position(anna).orElseThrow().position());
        assertEquals(List.of(ben), owners(book.plan(slots(Map.of(BEDWARS, List.of(slot("bw-1", 1)))),
                target -> true, 2_000L)));
        book.landed(ben);
        assertEquals(List.of(anna), owners(book.plan(slots(Map.of(BEDWARS, List.of(slot("bw-1", 1)))),
                target -> true, 6_000L)));
    }

    @Test
    void repeatedFailuresTakeThePlayerOutOfTheQueue() {
        book.join(BEDWARS, anna, List.of(), 0, true, 100L);
        for (int attempt = 1; attempt < 3; attempt++) {
            book.plan(slots(Map.of(BEDWARS, List.of(slot("bw-1", 1)))), target -> true, attempt * 10_000L);
            assertTrue(book.failed(anna, attempt * 10_000L, 1_000L, 3).isEmpty());
        }
        book.plan(slots(Map.of(BEDWARS, List.of(slot("bw-1", 1)))), target -> true, 40_000L);
        Optional<QueueBook.Removal> removal = book.failed(anna, 40_000L, 1_000L, 3);
        assertEquals(QueueLeftEvent.Reason.FAILED, removal.orElseThrow().reason());
        assertFalse(book.queued(anna));
    }

    @Test
    void heldBackSendsReturnWithoutCountingAsFailure() {
        book.join(BEDWARS, anna, List.of(), 0, true, 100L);
        book.plan(slots(Map.of(BEDWARS, List.of(slot("bw-1", 1)))), target -> true, 1_000L);
        assertTrue(book.retry(anna, 1_000L, 5_000L));
        assertTrue(book.plan(slots(Map.of(BEDWARS, List.of(slot("bw-1", 1)))), target -> true, 2_000L).isEmpty());
        assertEquals(1, book.plan(slots(Map.of(BEDWARS, List.of(slot("bw-1", 1)))), target -> true, 6_000L).size());
    }

    @Test
    void staffCanPushTheNextUnitEvenWhenPausedOrFull() {
        book.join(BEDWARS, anna, List.of(), 0, true, 100L);
        book.join(BEDWARS, ben, List.of(), 0, true, 200L);
        book.pause(BEDWARS);
        book.offline(anna, 300L);
        Optional<QueueBook.Assignment> pushed = book.force(BEDWARS, List.of(new QueueBook.Slot("bw-1",
                QueueBook.Slot.UNLIMITED, 1, 12)), 400L);
        assertEquals(ben, pushed.orElseThrow().owner());
        assertEquals(Optional.empty(), book.force(SKYWARS, List.of(slot("sw-1", 1)), 400L));
    }

    @Test
    void clearingRemovesEveryone() {
        book.join(BEDWARS, anna, List.of(ben), 0, true, 100L);
        book.join(BEDWARS, cara, List.of(), 0, true, 200L);
        List<QueueBook.Removal> removed = book.clear(BEDWARS, QueueLeftEvent.Reason.CLEARED);
        assertEquals(2, removed.size());
        assertEquals(3, removed.stream().mapToInt(removal -> removal.players().size()).sum());
        assertEquals(0, book.size(BEDWARS));
        assertTrue(book.empty());
    }

    @Test
    void shownPositionIsTheBestOneOfEveryPlayer() {
        book.join(BEDWARS, emil, List.of(), 0, false, 50L);
        book.join(BEDWARS, anna, List.of(ben), 0, false, 100L);
        book.join(SKYWARS, ben, List.of(), 0, false, 200L);
        Map<UUID, QueuePosition> shown = book.shown();
        assertEquals(SKYWARS, shown.get(ben).target());
        assertEquals(2, shown.get(anna).position());
        assertEquals(3, shown.size());
    }

    @Test
    void staleFlightsAreReported() {
        book.join(BEDWARS, anna, List.of(ben), 0, true, 100L);
        book.plan(slots(Map.of(BEDWARS, List.of(slot("bw-1", 5)))), target -> true, 1_000L);
        assertTrue(book.stale(10_000L, 30_000L).isEmpty());
        List<QueueBook.Flight> stale = book.stale(31_000L, 30_000L);
        assertEquals(List.of(anna, ben), stale.getFirst().players());
        assertEquals("bw-1", book.flightOf(ben).orElseThrow().server());
    }
}
