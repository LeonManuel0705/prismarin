package net.prismarin.api.social;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record Clan(UUID id, String name, String tag, String color, ClanAccess access, UUID leader,
                   List<ClanMember> members, Set<UUID> invited, Map<String, String> data, long created) {

    public Clan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(leader, "leader");
        color = color == null ? "" : color;
        access = access == null ? ClanAccess.INVITE_ONLY : access;
        members = List.copyOf(members);
        invited = Collections.unmodifiableSet(new LinkedHashSet<>(invited));
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public Optional<ClanMember> member(UUID player) {
        for (ClanMember member : members) {
            if (member.uuid().equals(player)) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }

    public Optional<ClanRole> role(UUID player) {
        return member(player).map(ClanMember::role);
    }

    public boolean contains(UUID player) {
        return member(player).isPresent();
    }

    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    public boolean isInvited(UUID player) {
        return invited.contains(player);
    }

    public boolean hasColor() {
        return !color.isEmpty();
    }

    public int size() {
        return members.size();
    }

    public List<UUID> memberIds() {
        return members.stream().map(ClanMember::uuid).toList();
    }

    public List<ClanMember> withRole(ClanRole role) {
        return members.stream().filter(member -> member.role() == role).toList();
    }

    public Optional<String> data(String key) {
        return Optional.ofNullable(data.get(key));
    }

    public long number(String key, long fallback) {
        String value = data.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean can(UUID actor, ClanAction action, UUID target, ClanRights rights) {
        return ClanRules.allowed(action, role(actor).orElse(null), target == null ? null : role(target).orElse(null),
                rights);
    }
}
