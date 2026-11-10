package net.prismarin.velocity.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FriendRulesTest {

    private static final long HOUR = 3_600_000L;
    private static final long EXPIRY = 72 * HOUR;
    private static final long NOW = 1_000 * HOUR;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    @Test
    void sendsAFreshRequest() {
        assertEquals(FriendRules.RequestOutcome.SENT, request(SocialProfile.empty(alice), SocialProfile.empty(bob),
                SocialSetting.EVERYONE));
    }

    @Test
    void refusesYourselfAndExistingFriends() {
        assertEquals(FriendRules.RequestOutcome.SELF, request(SocialProfile.empty(alice), SocialProfile.empty(alice),
                SocialSetting.EVERYONE));
        SocialProfile friends = SocialProfile.empty(alice).withFriend(bob, NOW);
        assertEquals(FriendRules.RequestOutcome.ALREADY_FRIENDS, request(friends, SocialProfile.empty(bob),
                SocialSetting.EVERYONE));
    }

    @Test
    void doesNotSendTwiceUntilTheRequestExpires() {
        SocialProfile sender = SocialProfile.empty(alice).withOutgoing(bob, NOW - HOUR);
        assertEquals(FriendRules.RequestOutcome.ALREADY_SENT, request(sender, SocialProfile.empty(bob),
                SocialSetting.EVERYONE));
        SocialProfile old = SocialProfile.empty(alice).withOutgoing(bob, NOW - EXPIRY);
        assertEquals(FriendRules.RequestOutcome.SENT, request(old, SocialProfile.empty(bob), SocialSetting.EVERYONE));
    }

    @Test
    void mutualRequestsBecomeAFriendship() {
        SocialProfile sender = SocialProfile.empty(alice).withIncoming(bob, NOW - HOUR);
        assertEquals(FriendRules.RequestOutcome.ACCEPTED, request(sender, SocialProfile.empty(bob),
                SocialSetting.NOBODY));
    }

    @Test
    void respectsThePrivacyOfTheTarget() {
        assertEquals(FriendRules.RequestOutcome.NOT_ACCEPTING, request(SocialProfile.empty(alice),
                SocialProfile.empty(bob), SocialSetting.NOBODY));
        assertEquals(FriendRules.RequestOutcome.NOT_ACCEPTING, request(SocialProfile.empty(alice),
                SocialProfile.empty(bob), SocialSetting.FRIENDS_OF_FRIENDS));
        SocialProfile sender = SocialProfile.empty(alice).withFriend(carol, NOW);
        SocialProfile target = SocialProfile.empty(bob).withFriend(carol, NOW);
        assertEquals(FriendRules.RequestOutcome.SENT, request(sender, target, SocialSetting.FRIENDS_OF_FRIENDS));
    }

    @Test
    void checksBothFriendLimits() {
        SocialProfile full = SocialProfile.empty(alice).withFriend(carol, NOW);
        assertEquals(FriendRules.RequestOutcome.SENDER_FULL, FriendRules.request(full, SocialProfile.empty(bob), 1, 5,
                SocialSetting.EVERYONE, NOW, EXPIRY));
        SocialProfile fullTarget = SocialProfile.empty(bob).withFriend(carol, NOW);
        assertEquals(FriendRules.RequestOutcome.TARGET_FULL, FriendRules.request(SocialProfile.empty(alice), fullTarget,
                5, 1, SocialSetting.EVERYONE, NOW, EXPIRY));
    }

    @Test
    void acceptsOnlyOpenRequestsWithRoom() {
        SocialProfile accepter = SocialProfile.empty(bob).withIncoming(alice, NOW - HOUR);
        assertEquals(FriendRules.AcceptOutcome.ACCEPTED, FriendRules.accept(accepter, SocialProfile.empty(alice), 5, 5,
                NOW, EXPIRY));
        assertEquals(FriendRules.AcceptOutcome.NO_REQUEST, FriendRules.accept(SocialProfile.empty(bob),
                SocialProfile.empty(alice), 5, 5, NOW, EXPIRY));
        SocialProfile expired = SocialProfile.empty(bob).withIncoming(alice, NOW - EXPIRY - 1);
        assertEquals(FriendRules.AcceptOutcome.NO_REQUEST, FriendRules.accept(expired, SocialProfile.empty(alice), 5, 5,
                NOW, EXPIRY));
        assertEquals(FriendRules.AcceptOutcome.SELF_FULL, FriendRules.accept(accepter.withFriend(carol, NOW),
                SocialProfile.empty(alice), 1, 5, NOW, EXPIRY));
        assertEquals(FriendRules.AcceptOutcome.OTHER_FULL, FriendRules.accept(accepter,
                SocialProfile.empty(alice).withFriend(carol, NOW), 5, 1, NOW, EXPIRY));
        assertEquals(FriendRules.AcceptOutcome.ALREADY_FRIENDS, FriendRules.accept(
                SocialProfile.empty(bob).withFriend(alice, NOW), SocialProfile.empty(alice), 5, 5, NOW, EXPIRY));
    }

    @Test
    void zeroExpiryKeepsRequestsForever() {
        assertFalse(FriendRules.expired(0L, Long.MAX_VALUE / 2, 0L));
        assertTrue(FriendRules.expired(0L, EXPIRY, EXPIRY));
    }

    @Test
    void picksTheHighestPermittedLimit() {
        Map<String, Integer> extra = new LinkedHashMap<>();
        extra.put("vip", 200);
        extra.put("mvp", 300);
        extra.put("tiny", 50);
        assertEquals(100, FriendRules.limit(100, extra, permission -> false));
        assertEquals(200, FriendRules.limit(100, extra, "vip"::equals));
        assertEquals(300, FriendRules.limit(100, extra, permission -> true));
        assertEquals(100, FriendRules.limit(100, extra, "tiny"::equals));
    }

    private FriendRules.RequestOutcome request(SocialProfile sender, SocialProfile target, String privacy) {
        return FriendRules.request(sender, target, 100, 100, privacy, NOW, EXPIRY);
    }
}
