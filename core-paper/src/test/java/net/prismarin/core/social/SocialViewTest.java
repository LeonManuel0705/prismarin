package net.prismarin.core.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SocialViewTest {

    private final UUID friend = UUID.randomUUID();
    private final UUID leader = UUID.randomUUID();

    @Test
    void readsAFriendPage() {
        SocialView view = parse("{\"type\":\"social-view\",\"id\":4,\"view\":\"friends\","
                + "\"features\":{\"friends\":true,\"party\":true,\"messages\":false},"
                + "\"counts\":{\"friends\":1,\"requests\":2},"
                + "\"friends\":{\"page\":0,\"pages\":3,\"total\":61,\"online\":1,\"limit\":100,\"can-invite\":true,"
                + "\"entries\":[{\"uuid\":\"" + friend + "\",\"name\":\"Alex\",\"online\":true,\"server\":\"lobby-1\","
                + "\"last-seen\":5,\"jump\":true}]}}");
        assertEquals(4, view.id());
        assertEquals(new SocialView.Features(true, true, false), view.features());
        assertEquals(2, view.counts().requests());
        assertEquals(3, view.friends().pages());
        assertTrue(view.friends().canInvite());
        assertEquals(List.of(new SocialView.Friend(friend, "Alex", true, "lobby-1", 5L, true)),
                view.friends().entries());
        assertNull(view.party());
        assertNull(view.requests());
    }

    @Test
    void masksNickedPartyMembers() {
        SocialView view = parse("{\"id\":1,\"party\":{\"in-party\":true,\"leader\":\"" + leader + "\","
                + "\"leader-self\":false,\"max\":8,\"members\":[{\"uuid\":\"00000000-0000-0000-0000-000000000000\","
                + "\"ref\":\"" + leader + "\",\"name\":\"Shadow\",\"masked\":true,\"leader\":true,\"online\":true}],"
                + "\"invited\":[],\"incoming\":[]}}");
        SocialView.Person member = view.party().members().getFirst();
        assertTrue(member.masked());
        assertFalse(member.showSkin());
        assertEquals(leader, member.ref());
        assertEquals("Shadow", member.name());
        assertEquals(leader, view.party().leader());
    }

    @Test
    void toleratesMissingAndBrokenFields() {
        SocialView view = parse("{\"id\":\"x\",\"features\":5,\"requests\":{\"incoming\":[{\"uuid\":\"nope\"}]},"
                + "\"settings\":[{\"key\":\"jump\",\"value\":\"on\",\"next\":\"off\",\"options\":[\"on\",\"off\"]},3]}");
        assertEquals(0, view.id());
        assertEquals(SocialView.Features.NONE, view.features());
        assertEquals(SocialView.NOBODY, view.requests().incoming().getFirst().uuid());
        assertEquals("?", view.requests().incoming().getFirst().name());
        assertEquals(List.of(new SocialView.Setting("jump", "on", "off", List.of("on", "off"))), view.settings());
    }

    private static SocialView parse(String json) {
        return SocialView.parse(JsonParser.parseString(json).getAsJsonObject());
    }
}
