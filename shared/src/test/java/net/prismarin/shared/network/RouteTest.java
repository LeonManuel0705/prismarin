package net.prismarin.shared.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RouteTest {

    private final UUID suspect = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void prefersAnyPlayerOtherThanTheAvoidedOne() {
        assertEquals(Optional.of(other), Route.choose(List.of(suspect, other), suspect, true).player());
        assertEquals(Optional.of(suspect), Route.choose(List.of(suspect, other), null, false).player());
    }

    @Test
    void fallsBackToTheBusBeforeUsingTheAvoidedPlayer() {
        assertEquals(Route.BUS, Route.choose(List.of(suspect), suspect, true));
        assertEquals(Optional.of(suspect), Route.choose(List.of(suspect), suspect, false).player());
    }

    @Test
    void anEmptyServerNeedsTheBus() {
        assertEquals(Route.BUS, Route.choose(List.of(), null, true));
        assertEquals(Route.NONE, Route.choose(List.of(), suspect, false));
        assertFalse(Route.NONE.reachable());
    }
}
