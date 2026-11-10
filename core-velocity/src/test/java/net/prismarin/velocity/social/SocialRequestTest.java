package net.prismarin.velocity.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
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

    @Test
    void roleActionsNeedATarget() {
        for (String action : new String[]{"party-promote", "party-demote", "party-transfer", "party-join",
                "friend-request"}) {
            assertTrue(SocialRequest.parse(json("{\"action\":\"" + action + "\"}")).isEmpty(), action);
            SocialRequest request = SocialRequest.parse(json("{\"action\":\"" + action + "\",\"target\":\""
                    + target + "\"}")).orElseThrow();
            assertEquals(target, request.target());
        }
        assertEquals(SocialRequest.Action.PARTY_DEMOTE,
                SocialRequest.parse(json("{\"action\":\"party-demote\",\"target\":\"" + target + "\"}"))
                        .orElseThrow().action());
    }

    @Test
    void partySettingsAreValidatedPerKey() {
        SocialRequest request = SocialRequest.parse(json("{\"action\":\"party-setting\",\"view\":\"party\","
                + "\"options\":{\"invite\":\"member\",\"access\":\"OPEN\",\"follow\":\"false\"}}")).orElseThrow();
        assertEquals(Map.of(PartyOption.INVITE, PartyOption.MEMBERS, PartyOption.ACCESS, PartyOption.OPEN,
                PartyOption.FOLLOW, SocialSetting.OFF), request.options());
        assertTrue(SocialRequest.parse(json("{\"action\":\"party-setting\"}")).isEmpty());
        assertTrue(SocialRequest.parse(json("{\"action\":\"party-setting\",\"options\":{}}")).isEmpty());
        assertTrue(SocialRequest.parse(json("{\"action\":\"party-setting\",\"options\":{\"op\":\"on\"}}"))
                .isEmpty());
        assertTrue(SocialRequest.parse(json("{\"action\":\"party-setting\",\"options\":{\"chat\":\"maybe\"}}"))
                .isEmpty());
        assertTrue(SocialRequest.parse(json("{\"action\":\"party-setting\",\"options\":[\"invite\"]}")).isEmpty());
        assertTrue(SocialRequest.parse(json("{\"action\":\"party-leave\"}")).orElseThrow().options().isEmpty());
    }

    private static JsonObject json(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }
}
