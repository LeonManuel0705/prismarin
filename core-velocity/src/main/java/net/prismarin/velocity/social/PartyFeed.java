package net.prismarin.velocity.social;

import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.social.Party;
import net.prismarin.api.social.PartyLeftEvent;
import net.prismarin.api.social.PartyMember;
import net.prismarin.shared.social.PartyChanges;
import net.prismarin.shared.social.PartyCodec;

public final class PartyFeed {

    public static final String TYPE = "party-sync";
    public static final String CAUSE_CHANGE = "change";
    public static final String CAUSE_SYNC = "sync";

    private final SocialContext context;

    public PartyFeed(SocialContext context) {
        this.context = context;
    }

    public Party view(PartyManager.Party party) {
        List<PartyMember> members = new ArrayList<>();
        for (UUID member : party.members()) {
            members.add(new PartyMember(member, context.nameOf(member), party.role(member)));
        }
        return new Party(party.id(), party.leader(), members, party.invites().keySet(), party.settings());
    }

    public void changed(PartyManager.Result result, PartyLeftEvent.Reason reason, UUID actor) {
        if (!result.ok() || result.before() == null && result.party() == null) {
            return;
        }
        Party before = result.before() == null ? null : view(result.before());
        Party after = result.party() == null ? null : view(result.party());
        for (PrismarinEvent event : PartyChanges.diff(before, after, result.disbanded(), reason, actor)) {
            context.publish(event);
        }
        Set<UUID> audience = new LinkedHashSet<>();
        for (Party party : new Party[]{before, after}) {
            if (party != null) {
                audience.addAll(party.memberIds());
                audience.addAll(party.invited());
            }
        }
        broadcast(audience, message(CAUSE_CHANGE, before, after, result.disbanded(), reason, actor));
    }

    public void arrived(Player player, Optional<PartyManager.Party> party) {
        party.ifPresent(current -> context.channel().sendSocial(player,
                message(CAUSE_SYNC, null, view(current), false, null, null)));
    }

    public static JsonObject message(String cause, Party before, Party after, boolean dissolved,
                                     PartyLeftEvent.Reason reason, UUID actor) {
        JsonObject message = new JsonObject();
        message.addProperty("type", TYPE);
        message.addProperty("cause", cause);
        if (before != null) {
            message.add("before", PartyCodec.encode(before));
        }
        if (after != null) {
            message.add("after", PartyCodec.encode(after));
        }
        message.addProperty("dissolved", dissolved);
        if (reason != null) {
            message.addProperty("reason", reason.id());
        }
        if (actor != null) {
            message.addProperty("actor", actor.toString());
        }
        return message;
    }

    private void broadcast(Set<UUID> audience, JsonObject message) {
        Map<String, Player> carriers = new LinkedHashMap<>();
        for (UUID uuid : audience) {
            context.proxy().getPlayer(uuid).ifPresent(player -> player.getCurrentServer().ifPresent(connection ->
                    carriers.putIfAbsent(connection.getServerInfo().getName(), player)));
        }
        for (Player carrier : carriers.values()) {
            context.channel().sendSocial(carrier, message.deepCopy());
        }
    }
}
