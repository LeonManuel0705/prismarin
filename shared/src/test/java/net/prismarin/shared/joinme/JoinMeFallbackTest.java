package net.prismarin.shared.joinme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.prismarin.api.joinme.JoinMeArrivedEvent.Placement;
import net.prismarin.api.joinme.JoinMeOutcome;
import org.junit.jupiter.api.Test;

class JoinMeFallbackTest {

    @Test
    void anIntegrationAlwaysTravelsToTheCreatorsServer() {
        assertEquals(JoinMeRoute.CONNECT, JoinMeRoute.plan(true, false, false, false, false));
        assertEquals(JoinMeRoute.ARRIVE, JoinMeRoute.plan(true, false, false, true, true));
        assertEquals(JoinMeRoute.ARRIVE, JoinMeRoute.plan(true, true, true, true, false));
    }

    @Test
    void sameServerAloneMovesPlayersFromOtherServers() {
        assertEquals(JoinMeRoute.CONNECT, JoinMeRoute.plan(false, true, false, false, false));
        assertEquals(JoinMeRoute.ALREADY_THERE, JoinMeRoute.plan(false, true, false, true, false));
    }

    @Test
    void sameWorldAloneOnlyWorksOnTheCreatorsServer() {
        assertEquals(JoinMeRoute.OTHER_SERVER, JoinMeRoute.plan(false, false, true, false, false));
        assertEquals(JoinMeRoute.ARRIVE, JoinMeRoute.plan(false, false, true, true, false));
        assertEquals(JoinMeRoute.ALREADY_THERE, JoinMeRoute.plan(false, false, true, true, true));
    }

    @Test
    void bothFallbacksTogetherMoveServerAndWorld() {
        assertEquals(JoinMeRoute.CONNECT, JoinMeRoute.plan(false, true, true, false, false));
        assertEquals(JoinMeRoute.ARRIVE, JoinMeRoute.plan(false, true, true, true, false));
        assertEquals(JoinMeRoute.ALREADY_THERE, JoinMeRoute.plan(false, true, true, true, true));
        assertTrue(JoinMeRoute.CONNECT.moves());
        assertFalse(JoinMeRoute.OTHER_SERVER.moves());
    }

    @Test
    void withoutIntegrationAndFallbacksThereIsNothingToJoin() {
        assertFalse(JoinMeRoute.joinable(false, false, false));
        assertTrue(JoinMeRoute.joinable(true, false, false));
        assertTrue(JoinMeRoute.joinable(false, true, false));
        assertTrue(JoinMeRoute.joinable(false, false, true));
    }

    @Test
    void theHandlerDecidesBeforeAnyFallback() {
        AtomicInteger calls = new AtomicInteger();
        assertEquals(Placement.HANDLER, ArrivalPlan.decide(true, () -> {
            calls.incrementAndGet();
            return JoinMeOutcome.HANDLED;
        }, true, ArrivalPlan.Position.CREATOR, true, true));
        assertEquals(Placement.REFUSED, ArrivalPlan.decide(true, () -> JoinMeOutcome.REFUSED, true,
                ArrivalPlan.Position.SPAWN, true, true));
        assertEquals(1, calls.get());
    }

    @Test
    void aHandlerCanHandThePlayerBackToTheFallback() {
        assertEquals(Placement.NEAR_CREATOR, ArrivalPlan.decide(true, () -> JoinMeOutcome.FALLBACK, true,
                ArrivalPlan.Position.CREATOR, true, true));
        assertEquals(Placement.WORLD_SPAWN, ArrivalPlan.decide(true, () -> null, true,
                ArrivalPlan.Position.SPAWN, true, true));
        assertEquals(Placement.WORLD_SPAWN, ArrivalPlan.decide(true, () -> {
            throw new IllegalStateException("broken plugin");
        }, true, ArrivalPlan.Position.SPAWN, true, false));
    }

    @Test
    void theWorldFallbackFollowsItsConfiguration() {
        AtomicInteger calls = new AtomicInteger();
        assertEquals(Placement.WORLD_SPAWN, ArrivalPlan.decide(false, () -> {
            calls.incrementAndGet();
            return JoinMeOutcome.HANDLED;
        }, true, ArrivalPlan.Position.SPAWN, true, true));
        assertEquals(0, calls.get());
        assertEquals(Placement.NEAR_CREATOR, ArrivalPlan.fallback(true, ArrivalPlan.Position.CREATOR, true, true));
        assertEquals(Placement.WORLD_SPAWN, ArrivalPlan.fallback(true, ArrivalPlan.Position.CREATOR, true, false));
        assertEquals(Placement.SERVER, ArrivalPlan.fallback(false, ArrivalPlan.Position.CREATOR, true, true));
        assertEquals(Placement.SERVER, ArrivalPlan.fallback(true, ArrivalPlan.Position.SPAWN, false, false));
        assertEquals(Optional.of(ArrivalPlan.Position.CREATOR), ArrivalPlan.Position.byId(" Creator "));
        assertEquals(Optional.empty(), ArrivalPlan.Position.byId("random"));
    }
}
