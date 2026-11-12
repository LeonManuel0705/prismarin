package net.prismarin.shared.joinme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeAudience;
import net.prismarin.api.joinme.JoinMeArrival;
import net.prismarin.api.joinme.JoinMeHandler;
import net.prismarin.api.joinme.JoinMeOutcome;
import net.prismarin.api.joinme.JoinMeScope;
import net.prismarin.api.joinme.JoinMeSpot;
import net.prismarin.api.server.GameState;
import org.junit.jupiter.api.Test;

class JoinMeHandlerRegistryTest {

    private final UUID creator = UUID.randomUUID();
    private final JoinMeHandlerRegistry registry = new JoinMeHandlerRegistry();

    private JoinMeSpot spot(String world) {
        return new JoinMeSpot(creator, "bw-1", "bedwars", world, GameState.WAITING);
    }

    private static JoinMeHandler handler(String id, int priority) {
        return new JoinMeHandler() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public JoinMeOutcome arrive(JoinMeArrival arrival) {
                return JoinMeOutcome.HANDLED;
            }
        };
    }

    @Test
    void withoutHandlersThereIsNoIntegration() {
        assertEquals(Optional.empty(), registry.select(spot("world")));
    }

    @Test
    void theMoreSpecificScopeWinsAtTheSamePriority() {
        JoinMeHandler server = handler("server", 0);
        JoinMeHandler group = handler("group", 0);
        JoinMeHandler arenas = handler("arenas", 0);
        JoinMeHandler custom = handler("custom", 0);
        registry.register(JoinMeScope.server(), server);
        registry.register(JoinMeScope.groups("bedwars"), group);
        registry.register(JoinMeScope.worlds("arena-1", "arena-2"), arenas);
        assertEquals(Optional.of(arenas), registry.select(spot("arena-2")));
        assertEquals(Optional.of(group), registry.select(spot("lobby")));
        registry.register(JoinMeScope.when(found -> found.world().startsWith("arena")), custom);
        assertEquals(Optional.of(custom), registry.select(spot("arena-1")));
    }

    @Test
    void priorityBeatsScopeAndEarlierRegistrationsBreakTies() {
        JoinMeHandler first = handler("first", 0);
        JoinMeHandler second = handler("second", 0);
        JoinMeHandler urgent = handler("urgent", 5);
        registry.register(JoinMeScope.worlds("arena-1"), first);
        registry.register(JoinMeScope.worlds("arena-1"), second);
        assertEquals(Optional.of(first), registry.select(spot("arena-1")));
        Subscription subscription = registry.register(JoinMeScope.server(), urgent);
        assertEquals(Optional.of(urgent), registry.select(spot("arena-1")));
        assertEquals(List.of(urgent, first, second), registry.handlers());
        subscription.unsubscribe();
        assertEquals(Optional.of(first), registry.select(spot("arena-1")));
        assertEquals(2, registry.size());
    }

    @Test
    void aBrokenScopeIsSkipped() {
        JoinMeHandler fine = handler("fine", 0);
        registry.register(JoinMeScope.when(found -> {
            throw new IllegalStateException("boom");
        }), handler("broken", 9));
        registry.register(JoinMeScope.server(), fine);
        assertEquals(Optional.of(fine), registry.select(spot("arena-1")));
    }

    @Test
    void anArrivalPrefersTheHandlerThatTookTheJoinMe() {
        JoinMeHandler bedwars = handler("bedwars", 0);
        JoinMeHandler other = handler("other", 9);
        registry.register(JoinMeScope.worlds("arena-1"), bedwars);
        registry.register(JoinMeScope.server(), other);
        JoinMe recorded = new JoinMe("k3f9a2", creator, "Lea", "Lea", "bw-1", "bedwars", "arena-1",
                GameState.WAITING, "bedwars", Map.of(), JoinMeAudience.NETWORK, 0, 0, 0L, 1L);
        assertEquals(Optional.of(bedwars), registry.forArrival(recorded, spot("lobby")));
        JoinMe gone = new JoinMe("k3f9a2", creator, "Lea", "Lea", "bw-1", "bedwars", "arena-1",
                GameState.WAITING, "uninstalled", Map.of(), JoinMeAudience.NETWORK, 0, 0, 0L, 1L);
        assertEquals(Optional.of(other), registry.forArrival(gone, spot("lobby")));
    }

    @Test
    void handlerIdsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> registry.register(JoinMeScope.server(),
                handler("Bad Id", 0)));
        assertThrows(NullPointerException.class, () -> registry.register(null, handler("ok", 0)));
    }
}
