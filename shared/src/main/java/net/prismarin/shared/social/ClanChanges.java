package net.prismarin.shared.social;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.ClanAccessChangedEvent;
import net.prismarin.api.social.ClanColorChangedEvent;
import net.prismarin.api.social.ClanCreatedEvent;
import net.prismarin.api.social.ClanDataChangedEvent;
import net.prismarin.api.social.ClanDisbandedEvent;
import net.prismarin.api.social.ClanInviteEvent;
import net.prismarin.api.social.ClanMember;
import net.prismarin.api.social.ClanMemberJoinedEvent;
import net.prismarin.api.social.ClanMemberLeftEvent;
import net.prismarin.api.social.ClanRenamedEvent;
import net.prismarin.api.social.ClanRole;
import net.prismarin.api.social.ClanRoleChangedEvent;

public final class ClanChanges {

    private ClanChanges() {
        throw new AssertionError();
    }

    public static List<PrismarinEvent> diff(Clan before, Clan after, boolean dissolved,
                                            ClanMemberLeftEvent.Reason reason, UUID actor) {
        List<PrismarinEvent> events = new ArrayList<>();
        Clan current = after != null ? after : before;
        if (current == null) {
            return events;
        }
        if (before == null) {
            events.add(new ClanCreatedEvent(current));
        }
        if (dissolved) {
            events.add(new ClanDisbandedEvent(current));
            return events;
        }
        Map<UUID, ClanRole> previous = roles(before);
        Map<UUID, ClanRole> next = roles(after);
        for (Map.Entry<UUID, ClanRole> entry : next.entrySet()) {
            ClanRole old = previous.get(entry.getKey());
            if (old == null) {
                if (before != null) {
                    events.add(new ClanMemberJoinedEvent(current, entry.getKey()));
                }
            } else if (old != entry.getValue()) {
                events.add(new ClanRoleChangedEvent(current, entry.getKey(), old, entry.getValue()));
            }
        }
        if (after == null) {
            return events;
        }
        for (UUID player : previous.keySet()) {
            if (!next.containsKey(player)) {
                events.add(new ClanMemberLeftEvent(current, player,
                        reason == null ? ClanMemberLeftEvent.Reason.LEFT : reason));
            }
        }
        for (UUID invited : after.invited()) {
            if (before == null || !before.invited().contains(invited)) {
                events.add(new ClanInviteEvent(current, actor == null ? current.leader() : actor, invited));
            }
        }
        if (before != null && !before.color().equals(after.color())) {
            events.add(new ClanColorChangedEvent(current, before.color()));
        }
        if (before != null && before.access() != after.access()) {
            events.add(new ClanAccessChangedEvent(current, before.access()));
        }
        if (before != null && (!before.name().equals(after.name()) || !before.tag().equals(after.tag()))) {
            events.add(new ClanRenamedEvent(current, before.name(), before.tag()));
        }
        if (before != null) {
            Set<String> keys = new LinkedHashSet<>(before.data().keySet());
            keys.addAll(after.data().keySet());
            for (String key : keys) {
                String old = before.data().get(key);
                String value = after.data().get(key);
                if (!Objects.equals(old, value)) {
                    events.add(new ClanDataChangedEvent(current, key, old, value));
                }
            }
        }
        return events;
    }

    private static Map<UUID, ClanRole> roles(Clan clan) {
        Map<UUID, ClanRole> roles = new LinkedHashMap<>();
        if (clan != null) {
            for (ClanMember member : clan.members()) {
                roles.put(member.uuid(), member.role());
            }
        }
        return roles;
    }
}
