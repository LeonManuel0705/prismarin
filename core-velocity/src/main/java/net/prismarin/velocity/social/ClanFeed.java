package net.prismarin.velocity.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.ClanMemberLeftEvent;
import net.prismarin.api.social.ClanRights;
import net.prismarin.shared.social.ClanChanges;
import net.prismarin.shared.social.ClanCodec;
import net.prismarin.shared.social.ClanTagFormat;
import net.prismarin.velocity.admin.ProxyRemote;

public final class ClanFeed {

    public static final String TYPE = "clan-sync";
    public static final String CAUSE_CHANGE = "change";
    public static final String CAUSE_SYNC = "sync";
    public static final String CAUSE_REMOTE = "remote";

    private final SocialContext context;

    public ClanFeed(SocialContext context) {
        this.context = context;
    }

    public void changed(ClanManager.Result result, ClanMemberLeftEvent.Reason reason, UUID actor) {
        if (!result.ok() || result.before() == null && result.clan() == null) {
            return;
        }
        for (PrismarinEvent event : ClanChanges.diff(result.before(), result.clan(), result.disbanded(), reason,
                actor)) {
            context.publish(event);
            ProxyRemote.relay(context.proxy(), event);
        }
        send(CAUSE_CHANGE, result.before(), result.after(), result.disbanded());
    }

    public void remote(Clan before, Clan after) {
        if (before == null && after == null) {
            return;
        }
        send(CAUSE_REMOTE, before, after, after == null);
    }

    public void arrived(Player player, Optional<Clan> clan) {
        clan.ifPresent(current -> context.channel().sendSocial(player,
                message(CAUSE_SYNC, null, current, false, display(), rights())));
    }

    private void send(String cause, Clan before, Clan after, boolean dissolved) {
        Set<UUID> audience = new LinkedHashSet<>();
        for (Clan clan : new Clan[]{before, after}) {
            if (clan != null) {
                audience.addAll(clan.memberIds());
                audience.addAll(clan.invited());
            }
        }
        JsonObject message = message(cause, before, after, dissolved, display(), rights());
        Map<String, Player> carriers = new LinkedHashMap<>();
        for (UUID uuid : audience) {
            context.proxy().getPlayer(uuid).ifPresent(player -> player.getCurrentServer().ifPresent(connection ->
                    carriers.putIfAbsent(connection.getServerInfo().getName(), player)));
        }
        for (Player carrier : carriers.values()) {
            context.channel().sendSocial(carrier, message.deepCopy());
        }
    }

    public static JsonObject message(String cause, Clan before, Clan after, boolean dissolved,
                                     ClanTagFormat display, ClanRights rights) {
        JsonObject message = new JsonObject();
        message.addProperty("type", TYPE);
        message.addProperty("cause", cause);
        Clan subject = after != null ? after : before;
        if (subject != null) {
            message.addProperty("id", subject.id().toString());
        }
        if (after != null && !dissolved) {
            message.add("clan", ClanCodec.encode(after));
        }
        JsonArray removed = new JsonArray();
        if (before != null) {
            for (UUID member : before.memberIds()) {
                if (dissolved || after == null || !after.contains(member)) {
                    removed.add(member.toString());
                }
            }
        }
        message.add("removed", removed);
        message.addProperty("dissolved", dissolved || after == null);
        message.add("display", ClanCodec.encodeDisplay(display));
        message.add("rights", ClanCodec.encodeRights(rights));
        return message;
    }

    private ClanTagFormat display() {
        return context.settings().clans().tag();
    }

    private ClanRights rights() {
        return context.settings().clans().rights();
    }
}
