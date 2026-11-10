package net.prismarin.velocity.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.prismarin.api.social.PartyAccess;
import net.prismarin.api.social.PartyRole;
import net.prismarin.api.social.PartySettings;
import org.junit.jupiter.api.Test;

class PartyOptionTest {

    @Test
    void keysAreFoundCaseInsensitive() {
        assertEquals(Optional.of(PartyOption.INVITE), PartyOption.byId("Invite"));
        assertEquals(Optional.of(PartyOption.ACCESS), PartyOption.byId(" access "));
        assertEquals(Optional.empty(), PartyOption.byId("op"));
        assertEquals(Optional.empty(), PartyOption.byId(null));
    }

    @Test
    void valuesAcceptCommonWords() {
        assertEquals(Optional.of(PartyOption.MODERATORS), PartyOption.INVITE.normalize("mods"));
        assertEquals(Optional.of(PartyOption.MEMBERS), PartyOption.INVITE.normalize("Everyone"));
        assertEquals(Optional.of(PartyOption.LEADER), PartyOption.INVITE.normalize("leiter"));
        assertEquals(Optional.of(PartyOption.MODERATORS), PartyOption.INVITE.normalize("moderator"));
        assertEquals(Optional.of(PartyOption.MEMBERS), PartyOption.CHAT.normalize("member"));
        assertEquals(Optional.of(PartyOption.OPEN), PartyOption.ACCESS.normalize("public"));
        assertEquals(Optional.of(PartyOption.INVITE_ONLY), PartyOption.ACCESS.normalize("invite only"));
        assertEquals(Optional.of(PartyOption.INVITE_ONLY), PartyOption.ACCESS.normalize("closed"));
        assertEquals(Optional.of(PartyOption.FRIENDS), PartyOption.ACCESS.normalize("Freunde"));
        assertEquals(Optional.of(SocialSetting.ON), PartyOption.FOLLOW.normalize("true"));
        assertEquals(Optional.of(SocialSetting.OFF), PartyOption.FOLLOW.normalize("aus"));
        assertEquals(Optional.empty(), PartyOption.FOLLOW.normalize("maybe"));
        assertEquals(Optional.empty(), PartyOption.ACCESS.normalize("moderators"));
        assertEquals(Optional.empty(), PartyOption.INVITE.normalize(null));
    }

    @Test
    void applyAndValueAreInverse() {
        PartySettings settings = PartySettings.DEFAULT;
        settings = PartyOption.INVITE.apply(settings, "members");
        settings = PartyOption.ACCESS.apply(settings, "open");
        settings = PartyOption.FOLLOW.apply(settings, "off");
        settings = PartyOption.CHAT.apply(settings, "moderators");
        assertEquals(new PartySettings(PartyRole.MEMBER, PartyAccess.OPEN, false, PartyRole.MODERATOR), settings);
        assertEquals("members", PartyOption.INVITE.value(settings));
        assertEquals("open", PartyOption.ACCESS.value(settings));
        assertEquals("off", PartyOption.FOLLOW.value(settings));
        assertEquals("moderators", PartyOption.CHAT.value(settings));
        assertEquals("moderators", PartyOption.INVITE.value(PartySettings.DEFAULT));
        assertEquals("invite-only", PartyOption.ACCESS.value(PartySettings.DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> PartyOption.ACCESS.apply(PartySettings.DEFAULT, "nope"));
    }

    @Test
    void nextCyclesThroughTheOptions() {
        assertEquals("members", PartyOption.INVITE.next("moderators"));
        assertEquals("leader", PartyOption.INVITE.next("members"));
        assertEquals("friends", PartyOption.ACCESS.next("invite-only"));
        assertEquals("invite-only", PartyOption.ACCESS.next("open"));
        assertEquals("off", PartyOption.FOLLOW.next("on"));
        assertEquals("invite-only", PartyOption.ACCESS.next("unknown"));
    }

    @Test
    void followIsOnlyOfferedWhileFollowingIsOn() {
        assertTrue(PartyOption.available(true).contains(PartyOption.FOLLOW));
        assertFalse(PartyOption.available(false).contains(PartyOption.FOLLOW));
        assertEquals(List.of(PartyOption.INVITE, PartyOption.ACCESS, PartyOption.CHAT), PartyOption.available(false));
    }
}
