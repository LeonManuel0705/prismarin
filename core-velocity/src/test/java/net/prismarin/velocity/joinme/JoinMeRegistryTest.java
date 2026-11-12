package net.prismarin.velocity.joinme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeAudience;
import net.prismarin.api.joinme.JoinMeResult;
import net.prismarin.api.server.GameState;
import net.prismarin.shared.joinme.JoinMeCodec;
import org.junit.jupiter.api.Test;

class JoinMeRegistryTest {

    private final UUID creator = UUID.randomUUID();
    private final JoinMeRegistry registry = new JoinMeRegistry(new Random(7));

    private JoinMe joinMe(String id, int maxJoins, long expires) {
        return new JoinMe(id, creator, "Lea", "Lea", "bw-1", "bedwars", "arena-1", GameState.WAITING, "",
                Map.of(), JoinMeAudience.NETWORK, maxJoins, 0, 0L, expires);
    }

    @Test
    void idsAreUniqueValidTokens() {
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < 500; index++) {
            String id = registry.newId();
            assertTrue(JoinMeCodec.validId(id), id);
            ids.add(id);
            registry.put(joinMe(id, 0, 10L), true);
        }
        assertEquals(500, ids.size());
        assertEquals(500, registry.size());
    }

    @Test
    void reservationsCountTowardsTheJoinLimit() {
        registry.put(joinMe("aaaa11", 2, 100L), true);
        assertEquals(JoinMeResult.Status.SUCCESS, registry.reserve("aaaa11", 0L));
        assertEquals(JoinMeResult.Status.SUCCESS, registry.reserve("aaaa11", 0L));
        assertEquals(JoinMeResult.Status.USED_UP, registry.reserve("aaaa11", 0L));
        registry.release("aaaa11");
        assertEquals(1, registry.get("aaaa11").orElseThrow().reserved());
        JoinMe joined = registry.arrived("aaaa11", true).orElseThrow();
        assertEquals(1, joined.joins());
        assertEquals(0, registry.get("aaaa11").orElseThrow().reserved());
        assertFalse(joined.usedUp());
        assertTrue(registry.arrived("aaaa11", false).orElseThrow().usedUp());
        assertEquals(JoinMeResult.Status.USED_UP, registry.reserve("aaaa11", 0L));
        assertEquals(JoinMeResult.Status.NOT_FOUND, registry.reserve("nope11", 0L));
        assertEquals(JoinMeResult.Status.EXPIRED, registry.reserve("aaaa11", 100L));
    }

    @Test
    void otherProxiesAddTheirJoins() {
        registry.put(joinMe("aaaa11", 0, 100L), true);
        assertEquals(3, registry.addJoins("aaaa11", 3).orElseThrow().joins());
        assertEquals(Optional.empty(), registry.addJoins("aaaa11", 0));
        assertEquals(Optional.empty(), registry.addJoins("zzzz99", 1));
        registry.put(registry.get("aaaa11").orElseThrow().joinMe().withJoins(4), false);
        assertFalse(registry.get("aaaa11").orElseThrow().worldFallback());
    }

    @Test
    void expiredJoinMesLeaveTheRegistry() {
        registry.put(joinMe("aaaa11", 0, 100L), true);
        registry.put(joinMe("bbbb22", 0, 500L), true);
        assertEquals(List.of("aaaa11"), registry.expire(100L).stream().map(JoinMe::id).toList());
        assertEquals(Optional.empty(), registry.get("aaaa11"));
        assertEquals(1, registry.all().size());
        assertEquals("bbbb22", registry.byCreator(creator).orElseThrow().joinMe().id());
        assertEquals(Optional.of(joinMe("bbbb22", 0, 500L)), registry.remove("bbbb22"));
        assertEquals(Optional.empty(), registry.byCreator(creator));
    }

    @Test
    void theCreatorsWorldFollowsHim() {
        registry.put(joinMe("aaaa11", 0, 100L), true);
        assertEquals("arena-2", registry.moved("aaaa11", "arena-2").orElseThrow().world());
        assertEquals(Optional.empty(), registry.moved("aaaa11", "arena-2"));
        assertEquals(Optional.empty(), registry.moved("aaaa11", ""));
    }

    @Test
    void cooldownsRunFromTheLastJoinMe() {
        assertEquals(0L, registry.cooldownLeft(creator, 60_000L, 0L));
        registry.stamp(creator, 1_000L);
        assertEquals(50_000L, registry.cooldownLeft(creator, 60_000L, 11_000L));
        assertEquals(0L, registry.cooldownLeft(creator, 60_000L, 61_000L));
        assertEquals(0L, registry.cooldownLeft(creator, 0L, 2_000L));
        registry.stamp(creator, 500L);
        assertEquals(50_000L, registry.cooldownLeft(creator, 60_000L, 11_000L));
        registry.forgetStamps(2_000L);
        assertEquals(0L, registry.cooldownLeft(creator, 60_000L, 11_000L));
    }

    @Test
    void pendingArrivalsWaitForTheRightServer() {
        PendingArrivals arrivals = new PendingArrivals();
        UUID player = UUID.randomUUID();
        JoinMe target = joinMe("aaaa11", 0, 100L);
        assertEquals(Optional.empty(), arrivals.expect(new PendingArrivals.Pending(player, target, false, true,
                1_000L)));
        assertEquals(Optional.empty(), arrivals.take(player, "lobby-1", 0L));
        assertEquals("bw-1", arrivals.take(player, "BW-1", 0L).orElseThrow().server());
        assertEquals(Optional.empty(), arrivals.take(player, "bw-1", 0L));
        arrivals.expect(new PendingArrivals.Pending(player, target, true, false, 1_000L));
        assertTrue(arrivals.expect(new PendingArrivals.Pending(player, target, false, true, 2_000L)).orElseThrow()
                .party());
        assertEquals(Optional.empty(), arrivals.take(player, "bw-1", 2_000L));
        assertEquals(1, arrivals.expired(2_000L).size());
        assertEquals(0, arrivals.size());
        arrivals.expect(new PendingArrivals.Pending(player, target, false, true, 2_000L));
        assertTrue(arrivals.forget(player).orElseThrow().reserved());
    }
}
