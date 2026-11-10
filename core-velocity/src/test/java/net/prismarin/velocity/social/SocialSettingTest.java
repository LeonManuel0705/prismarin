package net.prismarin.velocity.social;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SocialSettingTest {

    @Test
    void normalizesFriendlySpellings() {
        assertEquals(Optional.of(SocialSetting.ON), SocialSetting.JUMP.normalize("true"));
        assertEquals(Optional.of(SocialSetting.OFF), SocialSetting.JUMP.normalize(" AUS "));
        assertEquals(Optional.of(SocialSetting.FRIENDS_OF_FRIENDS), SocialSetting.REQUESTS.normalize("friends of friends"));
        assertEquals(Optional.of(SocialSetting.NOBODY), SocialSetting.MESSAGES.normalize("none"));
        assertEquals(Optional.empty(), SocialSetting.MESSAGES.normalize("friends-of-friends"));
        assertEquals(Optional.empty(), SocialSetting.JUMP.normalize(null));
    }

    @Test
    void cyclesThroughItsOptions() {
        assertEquals(SocialSetting.FRIENDS_OF_FRIENDS, SocialSetting.REQUESTS.next(SocialSetting.EVERYONE));
        assertEquals(SocialSetting.EVERYONE, SocialSetting.REQUESTS.next(SocialSetting.NOBODY));
        assertEquals(SocialSetting.OFF, SocialSetting.NOTIFICATIONS.next(SocialSetting.ON));
        assertEquals(SocialSetting.ON, SocialSetting.NOTIFICATIONS.next("unknown"));
    }

    @Test
    void findsSettingsById() {
        assertEquals(Optional.of(SocialSetting.PARTY_INVITES), SocialSetting.byId("party_invites"));
        assertEquals(Optional.empty(), SocialSetting.byId("op"));
    }
}
