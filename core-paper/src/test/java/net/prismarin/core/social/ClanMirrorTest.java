package net.prismarin.core.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.ClanAccess;
import net.prismarin.api.social.ClanAction;
import net.prismarin.api.social.ClanMember;
import net.prismarin.api.social.ClanRights;
import net.prismarin.api.social.ClanRole;
import net.prismarin.shared.social.ClanCodec;
import net.prismarin.shared.social.ClanTagFormat;
import org.junit.jupiter.api.Test;

class ClanMirrorTest {

    private final UUID leader = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID away = UUID.randomUUID();
    private final Predicate<UUID> here = uuid -> !uuid.equals(away);
    private final Clan clan = new Clan(UUID.randomUUID(), "Nightowls", "OWL", "gold", ClanAccess.OPEN, leader,
            List.of(new ClanMember(leader, "Leon", ClanRole.LEADER, 1L),
                    new ClanMember(member, "Alex", ClanRole.MEMBER, 2L),
                    new ClanMember(away, "Sam", ClanRole.MEMBER, 3L)), Set.of(), Map.of(), 1L);

    private static JsonObject sync(Clan clan, List<UUID> removed, boolean dissolved) {
        JsonObject message = new JsonObject();
        message.addProperty("type", ClanMirror.TYPE);
        message.addProperty("id", clan.id().toString());
        if (!dissolved) {
            message.add("clan", ClanCodec.encode(clan));
        }
        JsonArray gone = new JsonArray();
        removed.forEach(uuid -> gone.add(uuid.toString()));
        message.add("removed", gone);
        message.addProperty("dissolved", dissolved);
        message.add("display", ClanCodec.encodeDisplay(new ClanTagFormat("%name%", ClanTagFormat.Position.BEFORE,
                true, false)));
        message.add("rights", ClanCodec.encodeRights(ClanRights.DEFAULT.with(ClanAction.CHAT, ClanRole.MODERATOR)));
        return message;
    }

    @Test
    void syncedClansAreKnownForPlayersOnThisServer() {
        ClanMirror mirror = new ClanMirror();
        AtomicInteger changes = new AtomicInteger();
        mirror.onChange(changes::incrementAndGet);
        mirror.receive(sync(clan, List.of(), false), here);
        assertEquals(Optional.of(clan), mirror.clanOf(member));
        assertEquals(Optional.of(clan), mirror.clan(clan.id()));
        assertTrue(mirror.display().before());
        assertFalse(mirror.display().tablist());
        assertEquals(ClanRole.MODERATOR, mirror.rights().role(ClanAction.CHAT));
        assertEquals(1, changes.get());
    }

    @Test
    void removedMembersAndDissolvedClansDisappear() {
        ClanMirror mirror = new ClanMirror();
        mirror.receive(sync(clan, List.of(), false), here);
        Clan smaller = new Clan(clan.id(), clan.name(), clan.tag(), clan.color(), clan.access(), leader,
                clan.members().subList(0, 1), Set.of(), Map.of(), 1L);
        mirror.receive(sync(smaller, List.of(member, away), false), here);
        assertEquals(Optional.empty(), mirror.clanOf(member));
        assertEquals(Optional.of(smaller), mirror.clanOf(leader));
        mirror.receive(sync(smaller, List.of(leader), true), here);
        assertEquals(Optional.empty(), mirror.clanOf(leader));
        assertEquals(0, mirror.size());
    }

    @Test
    void clansWithNobodyHereAreNotKeptAndQuitsCleanUp() {
        ClanMirror mirror = new ClanMirror();
        Clan elsewhere = new Clan(UUID.randomUUID(), "Larks", "LRK", "", ClanAccess.OPEN, away,
                List.of(new ClanMember(away, "Sam", ClanRole.LEADER, 1L)), Set.of(), Map.of(), 1L);
        mirror.receive(sync(elsewhere, List.of(), false), here);
        assertEquals(0, mirror.size());
        mirror.receive(sync(clan, List.of(), false), here);
        mirror.forget(member, uuid -> uuid.equals(leader));
        assertEquals(Optional.of(clan), mirror.clanOf(leader));
        mirror.forget(leader, uuid -> false);
        assertEquals(0, mirror.size());
    }

    @Test
    void viewsCarryClanBadgesCommandsAndTheClanMenu() {
        UUID friend = UUID.randomUUID();
        SocialView view = SocialView.parse(JsonParser.parseString("{\"id\":3,\"view\":\"clan\","
                + "\"features\":{\"friends\":true,\"clans\":true,\"settings\":false},"
                + "\"commands\":{\"clans\":[\"info\",\"leave\"],\"party\":[]},"
                + "\"clan-display\":{\"format\":\"<%name%>\",\"position\":\"after\",\"chat\":true,\"tablist\":true},"
                + "\"friends\":{\"entries\":[{\"uuid\":\"" + friend + "\",\"name\":\"Alex\",\"clan\":{\"id\":\""
                + clan.id() + "\",\"name\":\"Nightowls\",\"tag\":\"OWL\",\"color\":\"gold\",\"size\":3}}]},"
                + "\"clan\":{\"in-clan\":true,\"can-create\":false,\"badge\":{\"id\":\"" + clan.id()
                + "\",\"name\":\"Nightowls\",\"tag\":\"OWL\",\"color\":\"gold\",\"size\":3},\"access\":\"open\","
                + "\"role\":\"co-leader\",\"max\":20,\"can-invite\":true,\"can-settings\":true,\"can-disband\":false,"
                + "\"can-leave\":true,\"members\":[{\"uuid\":\"" + leader + "\",\"name\":\"Leon\",\"role\":\"leader\","
                + "\"leader\":true,\"online\":true,\"server\":\"lobby\"},{\"uuid\":\"" + member
                + "\",\"name\":\"Alex\",\"role\":\"co-leader\",\"self\":true,\"can-kick\":true}],"
                + "\"invited\":[],\"incoming\":[]}}").getAsJsonObject());
        assertTrue(view.features().clans());
        assertFalse(view.features().settings());
        assertTrue(view.enabled("clans", "leave"));
        assertFalse(view.enabled("clans", "disband"));
        assertFalse(view.enabled("party", "warp"));
        assertTrue(view.enabled("friends", "jump"));
        assertEquals("<%name%>", view.clanDisplay().format());
        SocialView.ClanBadge badge = view.friends().entries().getFirst().clan();
        assertEquals("OWL", badge.tag());
        assertEquals(3, badge.size());
        SocialView.ClanView menu = view.clan();
        assertTrue(menu.inClan());
        assertEquals(ClanRole.CO_LEADER, menu.role());
        assertEquals(ClanAccess.OPEN, menu.access());
        assertEquals(List.of(ClanRole.LEADER, ClanRole.CO_LEADER),
                menu.members().stream().map(SocialView.ClanMemberView::role).toList());
        assertTrue(menu.members().get(1).person().permissions().kick());
        assertNull(SocialView.parse(JsonParser.parseString("{\"id\":1}").getAsJsonObject()).clan());
    }
}
