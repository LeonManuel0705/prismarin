package net.prismarin.velocity.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SocialRequestTest {

    private final UUID target = UUID.randomUUID();

    @Test
    void parsesAnAllowedActionWithItsView() {
        SocialRequest request = SocialRequest.parse(json("{\"id\":7,\"action\":\"friend-accept\",\"target\":\""
                + target + "\",\"view\":\"requests\",\"page\":2}")).orElseThrow();
        assertEquals(7, request.id());
        assertEquals(SocialRequest.Action.FRIEND_ACCEPT, request.action());
        assertEquals(target, request.target());
        assertEquals(SocialRequest.View.REQUESTS, request.view());
        assertEquals(2, request.page());
    }

    @Test
    void viewsNeedNoTarget() {
        SocialRequest request = SocialRequest.parse(json("{\"action\":\"view\",\"view\":\"party\"}")).orElseThrow();
        assertNull(request.target());
        assertEquals(SocialRequest.View.PARTY, request.view());
        assertEquals(SocialRequest.View.NONE,
                SocialRequest.parse(json("{\"action\":\"hello\"}")).orElseThrow().view());
    }

    @Test
    void rejectsAnythingOutsideTheAllowlist() {
        assertTrue(SocialRequest.parse(json("{\"action\":\"command\",\"value\":\"op me\"}")).isEmpty());
        assertTrue(SocialRequest.parse(json("{\"action\":\"friend-remove\"}")).isEmpty());
        assertTrue(SocialRequest.parse(json("{\"action\":\"friend-remove\",\"target\":\"bob\"}")).isEmpty());
        assertTrue(SocialRequest.parse(json("{\"action\":\"view\",\"view\":\"console\"}")).isEmpty());
        assertTrue(SocialRequest.parse(json("{\"action\":\"view\",\"view\":\"friends\",\"page\":-1}")).isEmpty());
        assertTrue(SocialRequest.parse(json("{\"action\":\"view\",\"view\":\"friends\",\"page\":99999}")).isEmpty());
        assertTrue(SocialRequest.parse(json("{\"action\":{\"nested\":true}}")).isEmpty());
    }

    @Test
    void settingsMustBeKnownKeysWithKnownValues() {
        SocialRequest request = SocialRequest.parse(json(
                "{\"action\":\"setting\",\"key\":\"messages\",\"value\":\"friends\",\"view\":\"settings\"}"))
                .orElseThrow();
        assertEquals(SocialSetting.MESSAGES, request.setting());
        assertEquals(SocialSetting.FRIENDS, request.value());
        assertTrue(SocialRequest.parse(json("{\"action\":\"setting\",\"key\":\"op\",\"value\":\"on\"}")).isEmpty());
        assertTrue(SocialRequest.parse(json("{\"action\":\"setting\",\"key\":\"jump\",\"value\":\"maybe\"}"))
                .isEmpty());
    }

    private static JsonObject json(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }
}
