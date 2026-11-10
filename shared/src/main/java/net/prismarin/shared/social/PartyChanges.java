package net.prismarin.shared.social;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.social.Party;
import net.prismarin.api.social.PartyCreatedEvent;
import net.prismarin.api.social.PartyDisbandedEvent;
import net.prismarin.api.social.PartyInviteEvent;
import net.prismarin.api.social.PartyJoinedEvent;
import net.prismarin.api.social.PartyLeftEvent;
import net.prismarin.api.social.PartyMember;
import net.prismarin.api.social.PartyRole;
import net.prismarin.api.social.PartyRoleChangedEvent;
import net.prismarin.api.social.PartySettingsChangedEvent;

public final class PartyChanges {

    private PartyChanges() {
        throw new AssertionError();
    }

    public static List<PrismarinEvent> diff(Party before, Party after, boolean dissolved, PartyLeftEvent.Reason reason,
                                            UUID actor) {
        List<PrismarinEvent> events = new ArrayList<>();
        Party current = after != null ? after : before;
        if (current == null) {
            return events;
        }
        if (before == null) {
            events.add(new PartyCreatedEvent(current));
        }
        Map<UUID, PartyRole> previous = roles(before);
        Map<UUID, PartyRole> next = roles(after);
        for (Map.Entry<UUID, PartyRole> entry : next.entrySet()) {
            UUID player = entry.getKey();
            PartyRole old = previous.get(player);
            if (old == null) {
                if (before != null || !player.equals(current.leader())) {
                    events.add(new PartyJoinedEvent(current, player));
                }
            } else if (old != entry.getValue()) {
                events.add(new PartyRoleChangedEvent(current, player, old, entry.getValue()));
            }
        }
        if (after != null) {
            for (UUID player : previous.keySet()) {
                if (!next.containsKey(player)) {
                    events.add(new PartyLeftEvent(current, player, reason == null ? PartyLeftEvent.Reason.LEFT : reason));
                }
            }
            for (UUID invited : after.invited()) {
                if (before == null || !before.invited().contains(invited)) {
                    events.add(new PartyInviteEvent(current, actor == null ? current.leader() : actor, invited));
                }
            }
            if (before != null && !before.settings().equals(after.settings())) {
                events.add(new PartySettingsChangedEvent(current, before.settings()));
            }
        }
        if (dissolved) {
            events.add(new PartyDisbandedEvent(current));
        }
        return events;
    }

    private static Map<UUID, PartyRole> roles(Party party) {
        Map<UUID, PartyRole> roles = new LinkedHashMap<>();
        if (party != null) {
            for (PartyMember member : party.members()) {
                roles.put(member.uuid(), member.role());
            }
        }
        return roles;
    }
}
