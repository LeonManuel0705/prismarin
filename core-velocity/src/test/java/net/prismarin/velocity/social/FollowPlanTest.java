package net.prismarin.velocity.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FollowPlanTest {

    private final UUID leader = UUID.randomUUID();
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID third = UUID.randomUUID();

    @Test
    void followsIntoGamesButNotIntoLobbiesByDefault() {
        assertTrue(FollowPlan.follows(true, false, Set.of(), "bedwars", false));
        assertFalse(FollowPlan.follows(true, false, Set.of(), "lobby", true));
        assertTrue(FollowPlan.follows(true, true, Set.of(), "lobby", true));
        assertFalse(FollowPlan.follows(false, true, Set.of(), "bedwars", false));
        assertFalse(FollowPlan.follows(true, false, Set.of("build"), "Build", false));
    }

    @Test
    void movesEveryoneWhenTheRoomIsUnknown() {
        FollowPlan plan = FollowPlan.plan(leader, "game-1", members(), -1);
        assertEquals(List.of(first, third), plan.move());
        assertTrue(plan.noRoom().isEmpty());
    }

    @Test
    void movesOnlyAsManyAsFit() {
        FollowPlan plan = FollowPlan.plan(leader, "game-1", members(), 1);
        assertEquals(List.of(first), plan.move());
        assertEquals(List.of(third), plan.noRoom());
        FollowPlan full = FollowPlan.plan(leader, "game-1", members(), 0);
        assertTrue(full.move().isEmpty());
        assertEquals(List.of(first, third), full.noRoom());
    }

    @Test
    void computesFreeSlots() {
        assertEquals(-1, FollowPlan.freeSlots(0, 10));
        assertEquals(4, FollowPlan.freeSlots(16, 12));
        assertEquals(0, FollowPlan.freeSlots(16, 20));
    }

    private List<FollowPlan.Member> members() {
        return List.of(new FollowPlan.Member(leader, "game-1"), new FollowPlan.Member(first, "lobby-1"),
                new FollowPlan.Member(second, "GAME-1"), new FollowPlan.Member(third, "lobby-2"));
    }
}
