package net.prismarin.api.social;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record Party(UUID id, UUID leader, List<PartyMember> members, Set<UUID> invited, PartySettings settings) {

    public Party {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(leader, "leader");
        members = List.copyOf(members);
        invited = Collections.unmodifiableSet(new LinkedHashSet<>(invited));
        settings = settings == null ? PartySettings.DEFAULT : settings;
    }

    public Optional<PartyMember> member(UUID player) {
        for (PartyMember member : members) {
            if (member.uuid().equals(player)) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }

    public Optional<PartyRole> role(UUID player) {
        return member(player).map(PartyMember::role);
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

    public int size() {
        return members.size();
    }

    public List<UUID> memberIds() {
        return members.stream().map(PartyMember::uuid).toList();
    }

    public List<PartyMember> withRole(PartyRole role) {
        return members.stream().filter(member -> member.role() == role).toList();
    }

    public boolean can(UUID actor, PartyAction action) {
        return can(actor, action, null);
    }

    public boolean can(UUID actor, PartyAction action, UUID target) {
        return PartyRules.allowed(action, role(actor).orElse(null), target == null ? null : role(target).orElse(null),
                settings);
    }
}
