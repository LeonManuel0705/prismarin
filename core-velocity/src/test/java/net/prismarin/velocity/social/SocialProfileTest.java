package net.prismarin.velocity.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SocialProfileTest {

    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID third = UUID.randomUUID();

    @Test
    void survivesAJsonRoundTrip() {
        SocialProfile profile = SocialProfile.empty(owner)
                .withFriend(other, 10L)
                .withIncoming(third, 20L)
                .withOutgoing(UUID.randomUUID(), 30L)
                .withSetting(SocialSetting.MESSAGES, SocialSetting.FRIENDS);
        assertEquals(profile, SocialProfile.fromJson(owner, profile.toJson()));
    }

    @Test
    void readsBrokenDocumentsAsEmpty() {
        assertEquals(SocialProfile.empty(owner), SocialProfile.fromJson(owner, "not json"));
        assertEquals(SocialProfile.empty(owner), SocialProfile.fromJson(owner, "[1,2]"));
        SocialProfile partial = SocialProfile.fromJson(owner,
                "{\"friends\":{\"nonsense\":1,\"" + other + "\":5},\"settings\":{\"jump\":\"off\"}}");
        assertEquals(Map.of(other, 5L), partial.friends());
        assertEquals(SocialSetting.OFF, partial.setting(SocialSetting.JUMP, SocialSetting.ON));
    }

    @Test
    void becomingFriendsClearsBothRequestDirections() {
        SocialProfile profile = SocialProfile.empty(owner).withIncoming(other, 1L).withOutgoing(other, 2L)
                .withFriend(other, 3L);
        assertTrue(profile.friendsWith(other));
        assertTrue(profile.incoming().isEmpty());
        assertTrue(profile.outgoing().isEmpty());
        assertFalse(profile.withoutFriend(other).friendsWith(other));
    }

    @Test
    void pruningDropsOnlyExpiredRequests() {
        SocialProfile profile = SocialProfile.empty(owner).withIncoming(other, 0L).withOutgoing(third, 90L);
        SocialProfile pruned = profile.pruned(100L, 50L);
        assertTrue(pruned.incoming().isEmpty());
        assertEquals(Map.of(third, 90L), pruned.outgoing());
        SocialProfile fresh = SocialProfile.empty(owner).withIncoming(other, 90L);
        assertSame(fresh, fresh.pruned(100L, 50L));
        assertSame(profile, profile.pruned(100L, 0L));
    }

    @Test
    void unknownSettingValuesFallBackToTheDefault() {
        SocialProfile profile = SocialProfile.empty(owner).withSetting(SocialSetting.REQUESTS, "sometimes");
        assertEquals(SocialSetting.NOBODY, profile.setting(SocialSetting.REQUESTS, SocialSetting.NOBODY));
        assertEquals(SocialSetting.EVERYONE, profile.setting(SocialSetting.REQUESTS, "garbage"));
    }
}
