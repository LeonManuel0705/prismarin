package net.prismarin.api.profile;

import java.util.UUID;
import net.prismarin.api.economy.Currency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerProfileTest {

    private PlayerProfile freshProfile() {
        return PlayerProfile.create(UUID.randomUUID(), "Leon", "de");
    }

    @Test
    void xpForNextLevelFollowsFormula() {
        PlayerProfile profile = freshProfile();
        assertEquals(500L, profile.xpForNextLevel());
        profile.setLevel(1);
        assertEquals(750L, profile.xpForNextLevel());
        profile.setLevel(10);
        assertEquals(3000L, profile.xpForNextLevel());
    }

    @Test
    void addXpWithoutThresholdKeepsLevel() {
        PlayerProfile profile = freshProfile();
        profile.addXp(499L);
        assertEquals(0, profile.getLevel());
        assertEquals(499L, profile.getXp());
    }

    @Test
    void addXpAtThresholdLevelsUpAndResetsXp() {
        PlayerProfile profile = freshProfile();
        profile.addXp(500L);
        assertEquals(1, profile.getLevel());
        assertEquals(0L, profile.getXp());
    }

    @Test
    void addXpCarriesSurplusAcrossMultipleLevelUps() {
        PlayerProfile profile = freshProfile();
        profile.addXp(1500L);
        assertEquals(2, profile.getLevel());
        assertEquals(250L, profile.getXp());
    }

    @Test
    void addXpRejectsNegativeAmounts() {
        PlayerProfile profile = freshProfile();
        assertThrows(IllegalArgumentException.class, () -> profile.addXp(-1L));
    }

    @Test
    void balanceAccessorsMapToCurrencies() {
        PlayerProfile profile = freshProfile();
        profile.balance(Currency.COINS, 120L);
        profile.balance(Currency.GEMS, 7L);
        assertEquals(120L, profile.balance(Currency.COINS));
        assertEquals(7L, profile.balance(Currency.GEMS));
        assertEquals(120L, profile.getCoins());
        assertEquals(7L, profile.getGems());
    }
}
