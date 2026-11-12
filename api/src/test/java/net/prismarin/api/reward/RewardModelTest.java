package net.prismarin.api.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.event.Subscription;
import org.junit.jupiter.api.Test;

class RewardModelTest {

    @Test
    void rewardsAddUp() {
        Reward day = new Reward(Map.of("coins", 50L), 25L, 0, List.of("say a"), List.of(), Map.of("crate", "1"));
        Reward milestone = new Reward(Map.of("coins", 10L, "gems", 5L), 0L, 1, List.of("say b"),
                List.of(NoticeText.of("Well done")), Map.of("crate", "2"));

        Reward both = day.plus(milestone);

        assertEquals(Map.of("coins", 60L, "gems", 5L), both.currencies());
        assertEquals(25L, both.xp());
        assertEquals(1, both.levels());
        assertEquals(List.of("say a", "say b"), both.commands());
        assertEquals(1, both.messages().size());
        assertEquals(Map.of("crate", "2"), both.custom());
        assertEquals(day, day.plus(Reward.EMPTY));
        assertEquals(day, Reward.EMPTY.plus(day));
    }

    @Test
    void currenciesAreNormalisedAndMustBePositive() {
        assertEquals(Map.of("coins", 5L), Reward.currency(" Coins ", 5L).currencies());
        assertThrows(IllegalArgumentException.class, () -> Reward.currency("coins", 0L));
        assertThrows(IllegalArgumentException.class, () -> Reward.experience(-1L));
        assertTrue(new Reward(null, 0L, 0, null, null, null).isEmpty());
        assertFalse(Reward.command("say hi").isEmpty());
        assertEquals(List.of(), Reward.command(" ").commands());
    }

    @Test
    void bigAmountsDoNotOverflow() {
        Reward huge = Reward.currency("coins", Long.MAX_VALUE).plus(Reward.currency("coins", 10L));

        assertEquals(Long.MAX_VALUE, huge.currencies().get("coins"));
    }

    @Test
    void handlersAreRegisteredByIdAndRemovedAgain() {
        RewardHandlers handlers = new RewardHandlers();
        RewardHandler crate = (context, value) -> { };
        Subscription subscription = handlers.register("Vote-Crate", crate);

        assertTrue(handlers.find("vote-crate").isPresent());
        assertEquals(Set.of("vote-crate"), handlers.ids());
        subscription.unsubscribe();
        assertTrue(handlers.find("vote-crate").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> handlers.register("bad id", crate));
    }

    @Test
    void claimsAndVotesKnowTheirState() {
        UUID player = UUID.randomUUID();
        DailyStatus status = new DailyStatus(player, 3, 5, 10L, false, 1L, 2L, 4);
        DailyClaim claim = new DailyClaim(DailyClaim.Result.CLAIMED, status, null, 3, false, 6);

        assertTrue(claim.claimed());
        assertTrue(claim.streakBroken());
        assertEquals(Reward.EMPTY, claim.reward());
        assertFalse(new Vote("id", null, "Steve", null, null, 1L, 0, null).known());
        assertEquals(player, new Vote("id", player, "Leon", "x", "", 1L, 0, Reward.EMPTY).playerId().orElseThrow());
        assertEquals("site", new VoteSite("site", " ", null, null).name());
    }
}
